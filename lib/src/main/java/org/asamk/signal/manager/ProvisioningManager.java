package org.asamk.signal.manager;

import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public interface ProvisioningManager {

    static ProvisioningManager init(
            File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) {
        return init(settingsPath, serviceEnvironment, userAgent, null);
    }

    static ProvisioningManager init(
            File settingsPath,
            ServiceEnvironment serviceEnvironment,
            String userAgent,
            Consumer<Manager> newManagerListener
    ) {
        var pathConfig = PathConfig.createDefault(settingsPath);

        final var serviceConfiguration = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);

        return new ProvisioningManagerImpl(pathConfig, serviceConfiguration, userAgent, newManagerListener);
    }

    URI getDeviceLinkUri() throws TimeoutException, IOException;

    String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException;
}
