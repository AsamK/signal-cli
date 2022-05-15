package org.asamk.signal.manager.config;

import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class ServiceEnvironmentConfig {

    private final ServiceEnvironment type;
    private final SignalServiceConfiguration signalServiceConfiguration;

    private final ECPublicKey unidentifiedSenderTrustRoot;

    private final KeyBackupConfig keyBackupConfig;

    private final String cdsMrenclave;

    public ServiceEnvironmentConfig(
            final ServiceEnvironment type,
            final SignalServiceConfiguration signalServiceConfiguration,
            final ECPublicKey unidentifiedSenderTrustRoot,
            final KeyBackupConfig keyBackupConfig,
            final String cdsMrenclave
    ) {
        this.type = type;
        this.signalServiceConfiguration = signalServiceConfiguration;
        this.unidentifiedSenderTrustRoot = unidentifiedSenderTrustRoot;
        this.keyBackupConfig = keyBackupConfig;
        this.cdsMrenclave = cdsMrenclave;
    }

    public ServiceEnvironment getType() {
        return type;
    }

    public SignalServiceConfiguration getSignalServiceConfiguration() {
        return signalServiceConfiguration;
    }

    public ECPublicKey getUnidentifiedSenderTrustRoot() {
        return unidentifiedSenderTrustRoot;
    }

    public KeyBackupConfig getKeyBackupConfig() {
        return keyBackupConfig;
    }

    public String getCdsMrenclave() {
        return cdsMrenclave;
    }
}
