package org.asamk.signal.manager.storage.protocol;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface SignalServiceAddressResolver {

    /**
     * Get a SignalServiceAddress with number and/or uuid from an identifier name.
     *
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    SignalServiceAddress resolveSignalServiceAddress(String identifier);
}
