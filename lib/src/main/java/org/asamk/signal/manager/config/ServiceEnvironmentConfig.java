package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public record ServiceEnvironmentConfig(
        ServiceEnvironment type,
        SignalServiceConfiguration signalServiceConfiguration,
        ECPublicKey unidentifiedSenderTrustRoot,
        String cdsiMrenclave,
        String svr2Mrenclave
) {}
