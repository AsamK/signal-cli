package org.asamk.signal.manager;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public interface MultiAccountManager extends AutoCloseable {

    List<Manager> getManagers();

    void addOnManagerAddedHandler(Consumer<Manager> handler);

    void removeOnManagerAddedHandler(Consumer<Manager> handler);

    void addOnManagerRemovedHandler(Consumer<Manager> handler);

    void removeOnManagerRemovedHandler(Consumer<Manager> handler);

    Manager getManager(String phoneNumber);

    URI getNewProvisioningDeviceLinkUri() throws TimeoutException, IOException;

    default URI getNewProvisioningDeviceLinkUri(final boolean importHistory) throws TimeoutException, IOException {
        if (importHistory) {
            throw new UnsupportedOperationException("History transfer is not supported by this manager backend");
        }
        return getNewProvisioningDeviceLinkUri();
    }

    ProvisioningManager getProvisioningManagerFor(URI deviceLinkUri);

    RegistrationManager getNewRegistrationManager(String account) throws IOException;

    @Override
    void close();
}
