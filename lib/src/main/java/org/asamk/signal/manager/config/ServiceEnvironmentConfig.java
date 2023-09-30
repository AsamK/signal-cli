package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.util.Collection;

public record ServiceEnvironmentConfig(
        ServiceEnvironment type,
        SignalServiceConfiguration signalServiceConfiguration,
        ECPublicKey unidentifiedSenderTrustRoot,
        KeyBackupConfig keyBackupConfig,
        Collection<KeyBackupConfig> fallbackKeyBackupConfigs,
        String cdsiMrenclave,
        String svr2Mrenclave
) {}
