package org.asamk.signal.manager.helper;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface SelfAddressProvider {

    SignalServiceAddress getSelfAddress();
}
