package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.util.Utils;
import org.signal.core.util.UptimeSleepTimer;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.network.api.AttachmentApi;
import org.signal.network.api.CallingApi;
import org.signal.network.api.CdsApi;
import org.signal.network.api.CertificateApi;
import org.signal.network.api.LinkDeviceApi;
import org.signal.network.api.RateLimitChallengeApi;
import org.signal.network.api.UsernameApi;
import org.signal.network.rest.SignalRestClient;
import org.signal.network.service.CdnService;
import org.signal.network.service.StorageServiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.account.AccountApi;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.keys.KeysApi;
import org.whispersystems.signalservice.api.keys.PreKeyRepository;
import org.whispersystems.signalservice.api.message.MessageApi;
import org.whispersystems.signalservice.api.profiles.ProfileApi;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.storage.StorageServiceApi;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.websocket.LibSignalChatConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SignalDependencies {

    private static final Logger logger = LoggerFactory.getLogger(SignalDependencies.class);

    private final Object LOCK = new Object();

    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final CredentialsProvider credentialsProvider;
    private final SignalServiceDataStore dataStore;
    private final int deviceId;
    private final ExecutorService executor;
    private final SignalSessionLock sessionLock;

    private boolean allowStories = true;

    private SignalServiceAccountManager accountManager;
    private AccountApi accountApi;
    private RateLimitChallengeApi rateLimitChallengeApi;
    private CdsApi cdsApi;
    private UsernameApi usernameApi;
    private GroupsV2Api groupsV2Api;
    private RegistrationApi registrationApi;
    private LinkDeviceApi linkDeviceApi;
    private StorageServiceApi storageServiceApi;
    private CertificateApi certificateApi;
    private AttachmentApi attachmentApi;
    private CallingApi callingApi;
    private MessageApi messageApi;
    private KeysApi keysApi;
    private GroupsV2Operations groupsV2Operations;
    private ClientZkOperations clientZkOperations;
    private ProfileService profileService;
    private ProfileApi profileApi;
    private CdnService cdnService;
    private PreKeyRepository preKeyRepository;
    private SignalRestClient signalRestClient;

    private PushServiceSocket pushServiceSocket;
    private Network libSignalNetwork;
    private SignalWebSocket.AuthenticatedWebSocket authenticatedSignalWebSocket;
    private SignalWebSocket.UnauthenticatedWebSocket unauthenticatedSignalWebSocket;
    private SignalServiceMessageReceiver messageReceiver;
    private SignalServiceMessageSender messageSender;

    private List<SecureValueRecovery> secureValueRecovery;

    SignalDependencies(
            final ServiceEnvironmentConfig serviceEnvironmentConfig,
            final String userAgent,
            final CredentialsProvider credentialsProvider,
            final SignalServiceDataStore dataStore,
            final int deviceId,
            final ExecutorService executor,
            final SignalSessionLock sessionLock
    ) {
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;
        this.credentialsProvider = credentialsProvider;
        this.dataStore = dataStore;
        this.deviceId = deviceId;
        this.executor = executor;
        this.sessionLock = sessionLock;
    }

    public void resetAfterAddressChange() {
        if (this.pushServiceSocket != null) {
            this.pushServiceSocket.close();
            this.pushServiceSocket = null;
            this.accountManager = null;
            this.messageReceiver = null;
            this.messageSender = null;
            this.profileService = null;
            this.groupsV2Api = null;
            this.registrationApi = null;
            this.secureValueRecovery = null;
        }
        if (this.authenticatedSignalWebSocket != null) {
            this.authenticatedSignalWebSocket.forceNewWebSocket();
        }
        if (this.unauthenticatedSignalWebSocket != null) {
            this.unauthenticatedSignalWebSocket.forceNewWebSocket();
        }
    }

    /**
     * This method needs to be called before the first websocket is created
     */
    public void setAllowStories(final boolean allowStories) {
        this.allowStories = allowStories;
    }

    public ServiceEnvironmentConfig getServiceEnvironmentConfig() {
        return serviceEnvironmentConfig;
    }

    public SignalSessionLock getSessionLock() {
        return sessionLock;
    }

    public PushServiceSocket getPushServiceSocket() {
        return getOrCreate(() -> pushServiceSocket,
                () -> pushServiceSocket = new PushServiceSocket(serviceEnvironmentConfig.signalServiceConfiguration(),
                        credentialsProvider,
                        userAgent,
                        ServiceConfig.AUTOMATIC_NETWORK_RETRY));
    }

    public Network getLibSignalNetwork() {
        return getOrCreate(() -> libSignalNetwork, () -> {
            libSignalNetwork = new Network(serviceEnvironmentConfig.netEnvironment(),
                    userAgent,
                    Map.of(),
                    Network.BuildVariant.PRODUCTION);
            setSignalNetworkProxy(libSignalNetwork);
        });
    }

    private void setSignalNetworkProxy(Network libSignalNetwork) {
        final var proxy = Utils.getHttpsProxy();
        if (proxy.address() instanceof InetSocketAddress addr) {
            switch (proxy.type()) {
                case Proxy.Type.DIRECT -> {
                }
                case Proxy.Type.HTTP -> {
                    try {
                        libSignalNetwork.setProxy("http", addr.getHostName(), addr.getPort(), null, null);
                    } catch (IOException e) {
                        logger.warn("Failed to set http proxy", e);
                    }
                }
                case Proxy.Type.SOCKS -> {
                    try {
                        libSignalNetwork.setProxy("socks", addr.getHostName(), addr.getPort(), null, null);
                    } catch (IOException e) {
                        logger.warn("Failed to set socks proxy", e);
                    }
                }
            }
        }
    }

    public SignalServiceAccountManager getAccountManager() {
        return getOrCreate(() -> accountManager,
                () -> accountManager = new SignalServiceAccountManager(getAuthenticatedSignalWebSocket(),
                        getAccountApi(),
                        getPushServiceSocket(),
                        getGroupsV2Operations()));
    }

    public SignalServiceAccountManager createUnauthenticatedAccountManager(String number, String password) {
        return SignalServiceAccountManager.createWithStaticCredentials(getServiceEnvironmentConfig().signalServiceConfiguration(),
                null,
                null,
                number,
                SignalServiceAddress.DEFAULT_DEVICE_ID,
                password,
                userAgent,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                ServiceConfig.GROUP_MAX_SIZE);
    }

    public AccountApi getAccountApi() {
        return getOrCreate(() -> accountApi, () -> accountApi = new AccountApi(getAuthenticatedSignalWebSocket()));
    }

    public RateLimitChallengeApi getRateLimitChallengeApi() {
        return getOrCreate(() -> rateLimitChallengeApi,
                () -> rateLimitChallengeApi = new RateLimitChallengeApi(getAuthenticatedSignalWebSocket()));
    }

    public CdsApi getCdsApi() {
        return getOrCreate(() -> cdsApi, () -> cdsApi = new CdsApi(getAuthenticatedSignalWebSocket()));
    }

    public UsernameApi getUsernameApi() {
        return getOrCreate(() -> usernameApi, () -> usernameApi = new UsernameApi(getUnauthenticatedSignalWebSocket()));
    }

    public GroupsV2Api getGroupsV2Api() {
        return getOrCreate(() -> groupsV2Api, () -> groupsV2Api = getAccountManager().getGroupsV2Api());
    }

    public RegistrationApi getRegistrationApi() {
        return getOrCreate(() -> registrationApi, () -> registrationApi = getAccountManager().getRegistrationApi());
    }

    public LinkDeviceApi getLinkDeviceApi() {
        return getOrCreate(() -> linkDeviceApi,
                () -> linkDeviceApi = new LinkDeviceApi(getAuthenticatedSignalWebSocket()));
    }

    private StorageServiceApi getStorageServiceApi() {
        return getOrCreate(() -> storageServiceApi,
                () -> storageServiceApi = new StorageServiceApi(getAuthenticatedSignalWebSocket(),
                        getPushServiceSocket()));
    }

    public StorageServiceService getStorageServiceRepository() {
        return new StorageServiceService(getStorageServiceApi());
    }

    public CertificateApi getCertificateApi() {
        return getOrCreate(() -> certificateApi,
                () -> certificateApi = new CertificateApi(getAuthenticatedSignalWebSocket()));
    }

    public AttachmentApi getAttachmentApi() {
        return getOrCreate(() -> attachmentApi,
                () -> attachmentApi = new AttachmentApi(getAuthenticatedSignalWebSocket(), getPushServiceSocket()));
    }

    public CallingApi getCallingApi() {
        return getOrCreate(() -> callingApi,
                () -> callingApi = new CallingApi(getAuthenticatedSignalWebSocket(),
                        getUnauthenticatedSignalWebSocket(),
                        getPushServiceSocket()));
    }

    public MessageApi getMessageApi() {
        return getOrCreate(() -> messageApi,
                () -> messageApi = new MessageApi(getAuthenticatedSignalWebSocket(),
                        getUnauthenticatedSignalWebSocket()));
    }

    public KeysApi getKeysApi() {
        return getOrCreate(() -> keysApi,
                () -> keysApi = new KeysApi(getAuthenticatedSignalWebSocket(), getUnauthenticatedSignalWebSocket()));
    }

    public GroupsV2Operations getGroupsV2Operations() {
        return getOrCreate(() -> groupsV2Operations,
                () -> groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceEnvironmentConfig.signalServiceConfiguration()),
                        ServiceConfig.GROUP_MAX_SIZE));
    }

    private ClientZkOperations getClientZkOperations() {
        return getOrCreate(() -> clientZkOperations,
                () -> clientZkOperations = ClientZkOperations.create(serviceEnvironmentConfig.signalServiceConfiguration()));
    }

    private ClientZkProfileOperations getClientZkProfileOperations() {
        final var clientZkOperations = getClientZkOperations();
        return clientZkOperations.getProfileOperations();
    }

    public SignalWebSocket.AuthenticatedWebSocket getAuthenticatedSignalWebSocket() {
        return getOrCreate(() -> authenticatedSignalWebSocket, () -> {
            final var timer = new UptimeSleepTimer();
            final var healthMonitor = new SignalWebSocketHealthMonitor(timer);

            authenticatedSignalWebSocket = new SignalWebSocket.AuthenticatedWebSocket(() -> new LibSignalChatConnection(
                    "normal",
                    getLibSignalNetwork(),
                    credentialsProvider,
                    allowStories,
                    healthMonitor), () -> true, timer, TimeUnit.SECONDS.toMillis(30));
            healthMonitor.monitor(authenticatedSignalWebSocket);
        });
    }

    public SignalWebSocket.UnauthenticatedWebSocket getUnauthenticatedSignalWebSocket() {
        return getOrCreate(() -> unauthenticatedSignalWebSocket, () -> {
            final var timer = new UptimeSleepTimer();
            final var healthMonitor = new SignalWebSocketHealthMonitor(timer);

            unauthenticatedSignalWebSocket = new SignalWebSocket.UnauthenticatedWebSocket(() -> new LibSignalChatConnection(
                    "unidentified",
                    getLibSignalNetwork(),
                    null,
                    allowStories,
                    healthMonitor), () -> true, timer, TimeUnit.SECONDS.toMillis(30));
            healthMonitor.monitor(unauthenticatedSignalWebSocket);
        });
    }

    public SignalServiceMessageReceiver getMessageReceiver() {
        return getOrCreate(() -> messageReceiver,
                () -> messageReceiver = new SignalServiceMessageReceiver(getPushServiceSocket()));
    }

    private SignalRestClient getSignalRestClient() {
        return getOrCreate(() -> signalRestClient,
                () -> signalRestClient = new SignalRestClient(serviceEnvironmentConfig.signalServiceConfiguration(),
                        userAgent,
                        credentialsProvider,
                        ServiceConfig.AUTOMATIC_NETWORK_RETRY));
    }

    public CdnService getCdnService() {
        return getOrCreate(() -> cdnService,
                () -> cdnService = new CdnService(getSignalRestClient(), getAttachmentApi()));
    }

    public PreKeyRepository getPreKeyRepository() {
        final SignalProtocolAddress localProtocolAddress = credentialsProvider.getAci().toProtocolAddress(deviceId);
        return getOrCreate(() -> preKeyRepository,
                () -> preKeyRepository = new PreKeyRepository(getKeysApi(),
                        dataStore.aci(),
                        localProtocolAddress,
                        Runnable::run));
    }

    public SignalServiceMessageSender getMessageSender() {
        return getOrCreate(() -> messageSender,
                () -> messageSender = new SignalServiceMessageSender(getPushServiceSocket(),
                        dataStore,
                        sessionLock,
                        getMessageApi(),
                        getKeysApi(),
                        Optional.empty(),
                        executor,
                        ServiceConfig.MAX_ENVELOPE_SIZE,
                        ServiceConfig.MAX_INCREMENTAL_MACS_PER_ENVELOPE,
                        () -> true,
                        getPreKeyRepository()));
    }

    public List<SecureValueRecovery> getSecureValueRecovery() {
        return getOrCreate(() -> secureValueRecovery,
                () -> secureValueRecovery = serviceEnvironmentConfig.svr2Mrenclaves()
                        .stream()
                        .map(mr -> (SecureValueRecovery) getAccountManager().getSecureValueRecoveryV2(mr))
                        .toList());
    }

    public ProfileApi getProfileApi() {
        return getOrCreate(() -> profileApi,
                () -> profileApi = new ProfileApi(getAuthenticatedSignalWebSocket(),
                        getUnauthenticatedSignalWebSocket(),
                        getPushServiceSocket(),
                        getClientZkProfileOperations()));
    }

    public ProfileService getProfileService() {
        return getOrCreate(() -> profileService,
                () -> profileService = new ProfileService(getClientZkProfileOperations(),
                        getAuthenticatedSignalWebSocket(),
                        getUnauthenticatedSignalWebSocket()));
    }

    public SignalServiceCipher getCipher(ServiceIdType serviceIdType) {
        final var certificateValidator = new CertificateValidator(serviceEnvironmentConfig.unidentifiedSenderTrustRoots());
        final var serviceId = serviceIdType == ServiceIdType.ACI
                ? credentialsProvider.getAci()
                : credentialsProvider.getPni();
        final var address = new SignalServiceAddress(serviceId, credentialsProvider.getE164());
        final var deviceId = credentialsProvider.getDeviceId();
        return new SignalServiceCipher(address,
                deviceId,
                serviceIdType == ServiceIdType.ACI ? dataStore.aci() : dataStore.pni(),
                sessionLock,
                certificateValidator);
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
