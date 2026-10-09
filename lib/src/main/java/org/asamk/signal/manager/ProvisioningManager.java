package org.asamk.signal.manager;

import org.asamk.signal.manager.api.UserAlreadyExistsException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public interface ProvisioningManager {

    URI getDeviceLinkUri() throws TimeoutException, IOException;

    default URI getDeviceLinkUri(final boolean importHistory) throws TimeoutException, IOException {
        if (importHistory) {
            throw new UnsupportedOperationException("History transfer is not supported by this provisioning backend");
        }
        return getDeviceLinkUri();
    }

    /**
     * Completes linking and returns the account's phone number, or ACI if it has no number.
     */
    String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException;

    default String finishDeviceLink(final String deviceName, final boolean importHistory)
            throws IOException, TimeoutException, UserAlreadyExistsException {
        if (importHistory) {
            throw new UnsupportedOperationException("History transfer is not supported by this provisioning backend");
        }
        return finishDeviceLink(deviceName);
    }
}
