package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.util.Collection;

public class ServiceEnvironmentConfig {

    private final ServiceEnvironment type;
    private final SignalServiceConfiguration signalServiceConfiguration;

    private final ECPublicKey unidentifiedSenderTrustRoot;

    private final KeyBackupConfig keyBackupConfig;
    private final Collection<KeyBackupConfig> fallbackKeyBackupConfigs;

    private final String cdsiMrenclave;
    private final String svr2Mrenclave;

    public ServiceEnvironmentConfig(
            final ServiceEnvironment type,
            final SignalServiceConfiguration signalServiceConfiguration,
            final ECPublicKey unidentifiedSenderTrustRoot,
            final KeyBackupConfig keyBackupConfig,
            final Collection<KeyBackupConfig> fallbackKeyBackupConfigs,
            final String cdsiMrenclave,
            final String svr2Mrenclave
    ) {
        this.type = type;
        this.signalServiceConfiguration = signalServiceConfiguration;
        this.unidentifiedSenderTrustRoot = unidentifiedSenderTrustRoot;
        this.keyBackupConfig = keyBackupConfig;
        this.fallbackKeyBackupConfigs = fallbackKeyBackupConfigs;
        this.cdsiMrenclave = cdsiMrenclave;
        this.svr2Mrenclave = svr2Mrenclave;
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

    public Collection<KeyBackupConfig> getFallbackKeyBackupConfigs() {
        return fallbackKeyBackupConfigs;
    }

    public String getCdsiMrenclave() {
        return cdsiMrenclave;
    }

    public String getSvr2Mrenclave() {
        return svr2Mrenclave;
    }
}
