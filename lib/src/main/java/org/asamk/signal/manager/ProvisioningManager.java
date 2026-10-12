package org.asamk.signal.manager;

import org.asamk.signal.manager.api.UserAlreadyExistsException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public interface ProvisioningManager {

    URI getDeviceLinkUri() throws TimeoutException, IOException;

    /**
     * Completes linking and returns the account's phone number, or ACI if it has no number.
     */
    String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException;
}
