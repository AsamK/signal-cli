package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Optional;

public interface RecipientTrustedResolver {

    RecipientId resolveSelfRecipientTrusted(RecipientAddress address);

    RecipientId resolveRecipientTrusted(SignalServiceAddress address);

    RecipientId resolveRecipientTrusted(Optional<ACI> aci, Optional<PNI> pni, Optional<String> number);
}
