package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.protocol.util.Medium;
import org.whispersystems.signalservice.api.account.AccountAttributes;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;

public class ServiceConfig {

    public static final int PREKEY_MINIMUM_COUNT = 10;
    public static final int PREKEY_BATCH_SIZE = 100;
    public static final int PREKEY_MAXIMUM_ID = Medium.MAX_VALUE;
    public static final long PREKEY_ARCHIVE_AGE = TimeUnit.DAYS.toMillis(30);
    public static final long PREKEY_STALE_AGE = TimeUnit.DAYS.toMillis(90);
    public static final long SIGNED_PREKEY_ROTATE_AGE = TimeUnit.DAYS.toMillis(2);

    public static final int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
    public static final long MAX_ENVELOPE_SIZE = 0;
    public static final long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = 10 * 1024 * 1024;
    public static final boolean AUTOMATIC_NETWORK_RETRY = true;
    public static final int GROUP_MAX_SIZE = 1001;
    public static final int MAXIMUM_ONE_OFF_REQUEST_SIZE = 3;
    public static final long UNREGISTERED_LIFESPAN = TimeUnit.DAYS.toMillis(30);

    public static AccountAttributes.Capabilities getCapabilities(boolean isPrimaryDevice) {
        final var deleteSync = !isPrimaryDevice;
        final var versionedExpirationTimer = !isPrimaryDevice;
        return new AccountAttributes.Capabilities(true, deleteSync, versionedExpirationTimer);
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
            case LIVE -> LiveConfig.getServiceEnvironmentConfig(interceptors);
            case STAGING -> StagingConfig.getServiceEnvironmentConfig(interceptors);
        };
    }
}
