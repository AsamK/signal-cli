package org.asamk.signal.manager.helper;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface UnidentifiedAccessProvider {

    Optional<UnidentifiedAccessPair> getAccessFor(SignalServiceAddress address);
}
