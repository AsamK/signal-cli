package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

import java.util.List;

public record ServiceEnvironmentConfig(
        ServiceEnvironment type,
        Network.Environment netEnvironment,
        SignalServiceConfiguration signalServiceConfiguration,
        ECPublicKey unidentifiedSenderTrustRoot,
        String cdsiMrenclave,
        List<String> svr2Mrenclaves
) {}
