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
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.util.concurrent.ExecutorService;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class SignalDependencies {

    private final SignalServiceAccountManager accountManager;
    private final GroupsV2Api groupsV2Api;
    private final GroupsV2Operations groupsV2Operations;

    private final SignalWebSocket signalWebSocket;
    private final SignalServiceMessageReceiver messageReceiver;
    private final SignalServiceMessageSender messageSender;

    private final KeyBackupService keyBackupService;
    private final ProfileService profileService;
    private final SignalServiceCipher cipher;

    public SignalDependencies(
            final SignalServiceAddress selfAddress,
            final ServiceEnvironmentConfig serviceEnvironmentConfig,
            final String userAgent,
            final DynamicCredentialsProvider credentialsProvider,
            final SignalServiceDataStore dataStore,
            final ExecutorService executor,
            final SignalSessionLock sessionLock
    ) {
        this.groupsV2Operations = capabilities.isGv2() ? new GroupsV2Operations(ClientZkOperations.create(
                serviceEnvironmentConfig.getSignalServiceConfiguration())) : null;
        final SleepTimer timer = new UptimeSleepTimer();
        this.accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                credentialsProvider,
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);
        this.groupsV2Api = accountManager.getGroupsV2Api();
        this.keyBackupService = accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
                serviceEnvironmentConfig.getKeyBackupConfig().getEnclaveName(),
                serviceEnvironmentConfig.getKeyBackupConfig().getServiceId(),
                serviceEnvironmentConfig.getKeyBackupConfig().getMrenclave(),
                10);
        final ClientZkProfileOperations clientZkProfileOperations = capabilities.isGv2() ? ClientZkOperations.create(
                serviceEnvironmentConfig.getSignalServiceConfiguration()).getProfileOperations() : null;
        this.messageReceiver = new SignalServiceMessageReceiver(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                credentialsProvider,
                userAgent,
                clientZkProfileOperations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);

        final var healthMonitor = new SignalWebSocketHealthMonitor(timer);
        final WebSocketFactory webSocketFactory = new WebSocketFactory() {
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
        this.signalWebSocket = new SignalWebSocket(webSocketFactory);
        healthMonitor.monitor(signalWebSocket);
        this.profileService = new ProfileService(clientZkProfileOperations, messageReceiver, signalWebSocket);

        final var certificateValidator = new CertificateValidator(serviceEnvironmentConfig.getUnidentifiedSenderTrustRoot());
        this.cipher = new SignalServiceCipher(selfAddress, dataStore, sessionLock, certificateValidator);
        this.messageSender = new SignalServiceMessageSender(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                credentialsProvider,
                dataStore,
                sessionLock,
                userAgent,
                signalWebSocket,
                Optional.absent(),
                clientZkProfileOperations,
                executor,
                ServiceConfig.MAX_ENVELOPE_SIZE,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);
    }

    public SignalServiceAccountManager getAccountManager() {
        return accountManager;
    }

    public GroupsV2Api getGroupsV2Api() {
        return groupsV2Api;
    }

    public GroupsV2Operations getGroupsV2Operations() {
        return groupsV2Operations;
    }

    public SignalWebSocket getSignalWebSocket() {
        return signalWebSocket;
    }

    public SignalServiceMessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public SignalServiceMessageSender getMessageSender() {
        return messageSender;
    }

    public KeyBackupService getKeyBackupService() {
        return keyBackupService;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public SignalServiceCipher getCipher() {
        return cipher;
    }
}
