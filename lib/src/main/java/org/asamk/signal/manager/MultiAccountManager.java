package org.asamk.signal.manager;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public interface MultiAccountManager extends AutoCloseable {

    List<String> getAccountNumbers();

    List<Manager> getManagers();

    void addOnManagerAddedHandler(Consumer<Manager> handler);

    void addOnManagerRemovedHandler(Consumer<Manager> handler);

    Manager getManager(String phoneNumber);

    URI getNewProvisioningDeviceLinkUri() throws TimeoutException, IOException;

    ProvisioningManager getProvisioningManagerFor(URI deviceLinkUri);

    RegistrationManager getNewRegistrationManager(String account) throws IOException;

    @Override
    void close();
}
