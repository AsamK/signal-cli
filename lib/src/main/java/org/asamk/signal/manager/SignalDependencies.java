package org.asamk.signal.manager;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class SignalDependencies {

    private final Object LOCK = new Object();

    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final DynamicCredentialsProvider credentialsProvider;
    private final SignalServiceDataStore dataStore;
    private final ExecutorService executor;
    private final SignalSessionLock sessionLock;

    private SignalServiceAccountManager accountManager;
    private GroupsV2Api groupsV2Api;
    private GroupsV2Operations groupsV2Operations;
    private ClientZkOperations clientZkOperations;

    private SignalWebSocket signalWebSocket;
    private SignalServiceMessageReceiver messageReceiver;
    private SignalServiceMessageSender messageSender;

    private KeyBackupService keyBackupService;
    private ProfileService profileService;
    private SignalServiceCipher cipher;

    public SignalDependencies(
            final ServiceEnvironmentConfig serviceEnvironmentConfig,
            final String userAgent,
            final DynamicCredentialsProvider credentialsProvider,
            final SignalServiceDataStore dataStore,
            final ExecutorService executor,
            final SignalSessionLock sessionLock
    ) {
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;
        this.credentialsProvider = credentialsProvider;
        this.dataStore = dataStore;
        this.executor = executor;
        this.sessionLock = sessionLock;
    }

    public SignalServiceAccountManager getAccountManager() {
        return getOrCreate(() -> accountManager,
                () -> accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                        credentialsProvider,
                        userAgent,
                        getGroupsV2Operations(),
                        ServiceConfig.AUTOMATIC_NETWORK_RETRY));
    }

    public GroupsV2Api getGroupsV2Api() {
        return getOrCreate(() -> groupsV2Api, () -> groupsV2Api = getAccountManager().getGroupsV2Api());
    }

    public GroupsV2Operations getGroupsV2Operations() {
        return getOrCreate(() -> groupsV2Operations,
                () -> groupsV2Operations = capabilities.isGv2() ? new GroupsV2Operations(ClientZkOperations.create(
                        serviceEnvironmentConfig.getSignalServiceConfiguration())) : null);
    }

    private ClientZkOperations getClientZkOperations() {
        return getOrCreate(() -> clientZkOperations,
                () -> clientZkOperations = capabilities.isGv2()
                        ? ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration())
                        : null);
    }

    private ClientZkProfileOperations getClientZkProfileOperations() {
        final var clientZkOperations = getClientZkOperations();
        return clientZkOperations == null ? null : clientZkOperations.getProfileOperations();
    }

    public SignalWebSocket getSignalWebSocket() {
        return getOrCreate(() -> signalWebSocket, () -> {
            final var timer = new UptimeSleepTimer();
            final var healthMonitor = new SignalWebSocketHealthMonitor(timer);
            final var webSocketFactory = new WebSocketFactory() {
                @Override
                public WebSocketConnection createWebSocket() {
                    return new WebSocketConnection("normal",
                            serviceEnvironmentConfig.getSignalServiceConfiguration(),
                            Optional.of(credentialsProvider),
                            userAgent,
                            healthMonitor);
                }

                @Override
                public WebSocketConnection createUnidentifiedWebSocket() {
                    return new WebSocketConnection("unidentified",
                            serviceEnvironmentConfig.getSignalServiceConfiguration(),
                            Optional.absent(),
                            userAgent,
                            healthMonitor);
                }
            };
            signalWebSocket = new SignalWebSocket(webSocketFactory);
            healthMonitor.monitor(signalWebSocket);
        });
    }

    public SignalServiceMessageReceiver getMessageReceiver() {
        return getOrCreate(() -> messageReceiver,
                () -> messageReceiver = new SignalServiceMessageReceiver(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                        credentialsProvider,
                        userAgent,
                        getClientZkProfileOperations(),
                        ServiceConfig.AUTOMATIC_NETWORK_RETRY));
    }

    public SignalServiceMessageSender getMessageSender() {
        return getOrCreate(() -> messageSender,
                () -> messageSender = new SignalServiceMessageSender(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                        credentialsProvider,
                        dataStore,
                        sessionLock,
                        userAgent,
                        getSignalWebSocket(),
                        Optional.absent(),
                        getClientZkProfileOperations(),
                        executor,
                        ServiceConfig.MAX_ENVELOPE_SIZE,
                        ServiceConfig.AUTOMATIC_NETWORK_RETRY));
    }

    public KeyBackupService getKeyBackupService() {
        return getOrCreate(() -> keyBackupService,
                () -> keyBackupService = getAccountManager().getKeyBackupService(ServiceConfig.getIasKeyStore(),
                        serviceEnvironmentConfig.getKeyBackupConfig().getEnclaveName(),
                        serviceEnvironmentConfig.getKeyBackupConfig().getServiceId(),
                        serviceEnvironmentConfig.getKeyBackupConfig().getMrenclave(),
                        10));
    }

    public ProfileService getProfileService() {
        return getOrCreate(() -> profileService,
                () -> profileService = new ProfileService(getClientZkProfileOperations(),
                        getMessageReceiver(),
                        getSignalWebSocket()));
    }

    public SignalServiceCipher getCipher() {
        return getOrCreate(() -> cipher, () -> {
            final var certificateValidator = new CertificateValidator(serviceEnvironmentConfig.getUnidentifiedSenderTrustRoot());
            final var address = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
            cipher = new SignalServiceCipher(address, dataStore, sessionLock, certificateValidator);
        });
    }

    private <T> T getOrCreate(Supplier<T> supplier, Callable creator) {
        var value = supplier.get();
        if (value != null) {
            return value;
        }

        synchronized (LOCK) {
            value = supplier.get();
            if (value != null) {
                return value;
            }
            creator.call();
            return supplier.get();
        }
    }

    private interface Callable {

        void call();
    }
}
