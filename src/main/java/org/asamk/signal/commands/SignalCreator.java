package org.asamk.signal.commands;

import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.config.ServiceEnvironment;

import java.io.File;
import java.io.IOException;

public interface SignalCreator {

    File getSettingsPath();

    ServiceEnvironment getServiceEnvironment();

    ProvisioningManager getNewProvisioningManager();

    RegistrationManager getNewRegistrationManager(String username) throws IOException;
}
