package org.asamk.signal.manager;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface MultiAccountManager extends AutoCloseable {

    List<String> getAccountNumbers();

    void addOnManagerAddedHandler(Consumer<Manager> handler);

    void addOnManagerRemovedHandler(Consumer<Manager> handler);

    Manager getManager(String phoneNumber);

    ProvisioningManager getNewProvisioningManager();

    RegistrationManager getNewRegistrationManager(String username) throws IOException;

    @Override
    void close();
}
