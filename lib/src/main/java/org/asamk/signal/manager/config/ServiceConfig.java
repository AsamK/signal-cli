package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.protocol.util.Medium;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;

public class ServiceConfig {

    public final static int PREKEY_MINIMUM_COUNT = 10;
    public final static int PREKEY_BATCH_SIZE = 100;
    public final static int PREKEY_MAXIMUM_ID = Medium.MAX_VALUE;
    public static final long PREKEY_ARCHIVE_AGE = TimeUnit.DAYS.toMillis(30);
    public static final long PREKEY_STALE_AGE = TimeUnit.DAYS.toMillis(90);
    public static final long SIGNED_PREKEY_ROTATE_AGE = TimeUnit.DAYS.toMillis(2);

    public final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
    public final static long MAX_ENVELOPE_SIZE = 0;
    public final static long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = 10 * 1024 * 1024;
    public final static boolean AUTOMATIC_NETWORK_RETRY = true;
    public final static int GROUP_MAX_SIZE = 1001;
    public final static int MAXIMUM_ONE_OFF_REQUEST_SIZE = 3;

    private final static KeyStore iasKeyStore;

    static {
        try {
            TrustStore contactTrustStore = new IasTrustStore();

            var keyStore = KeyStore.getInstance("BKS");
            keyStore.load(contactTrustStore.getKeyStoreInputStream(),
                    contactTrustStore.getKeyStorePassword().toCharArray());

            iasKeyStore = keyStore;
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public static AccountAttributes.Capabilities getCapabilities(boolean isPrimaryDevice) {
        final var giftBadges = !isPrimaryDevice;
        return new AccountAttributes.Capabilities(false, true, true, true, true, giftBadges, false, false);
    }

    public static KeyStore getIasKeyStore() {
        return iasKeyStore;
    }

    public static ServiceEnvironmentConfig getServiceEnvironmentConfig(
            ServiceEnvironment serviceEnvironment, String userAgent
    ) {
        final Interceptor userAgentInterceptor = chain -> chain.proceed(chain.request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build());

        final var interceptors = List.of(userAgentInterceptor);

        return switch (serviceEnvironment) {
            case LIVE -> new ServiceEnvironmentConfig(serviceEnvironment,
                    LiveConfig.createDefaultServiceConfiguration(interceptors),
                    LiveConfig.getUnidentifiedSenderTrustRoot(),
                    LiveConfig.createKeyBackupConfig(),
                    LiveConfig.createFallbackKeyBackupConfigs(),
                    LiveConfig.getCdsiMrenclave(),
                    LiveConfig.getSvr2Mrenclave());
            case STAGING -> new ServiceEnvironmentConfig(serviceEnvironment,
                    StagingConfig.createDefaultServiceConfiguration(interceptors),
                    StagingConfig.getUnidentifiedSenderTrustRoot(),
                    StagingConfig.createKeyBackupConfig(),
                    StagingConfig.createFallbackKeyBackupConfigs(),
                    StagingConfig.getCdsiMrenclave(),
                    StagingConfig.getSvr2Mrenclave());
        };
    }
}
