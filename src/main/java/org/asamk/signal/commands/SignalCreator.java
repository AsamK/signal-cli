package org.asamk.signal.commands;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface SignalCreator {

    List<String> getAccountNumbers();

    void addManager(Manager m);

    void addOnManagerAddedHandler(Consumer<Manager> handler);

    Manager getManager(String phoneNumber);

    ProvisioningManager getNewProvisioningManager();

    RegistrationManager getNewRegistrationManager(String username) throws IOException;
}
