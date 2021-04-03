package org.asamk.signal.commands;

import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;

import java.io.IOException;

public interface SignalCreator {

    ProvisioningManager getNewProvisioningManager();

    RegistrationManager getNewRegistrationManager(String username) throws IOException;
}
