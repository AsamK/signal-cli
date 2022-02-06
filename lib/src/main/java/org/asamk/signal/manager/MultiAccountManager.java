package org.asamk.signal.manager;

import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public interface MultiAccountManager extends AutoCloseable {

    static MultiAccountManager init(
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment,
            final String userAgent,
            final TrustNewIdentity trustNewIdentity
    ) {
        final var logger = LoggerFactory.getLogger(MultiAccountManager.class);
        final var managers = getAllLocalAccountNumbers(settingsPath).stream().map(a -> {
            try {
                return Manager.init(a, settingsPath, serviceEnvironment, userAgent, trustNewIdentity);
            } catch (NotRegisteredException | IOException | AccountCheckException e) {
                logger.warn("Ignoring {}: {} ({})", a, e.getMessage(), e.getClass().getSimpleName());
                return null;
            }
        }).filter(Objects::nonNull).toList();

        return new MultiAccountManagerImpl(managers, settingsPath, serviceEnvironment, userAgent);
    }

    static List<String> getAllLocalAccountNumbers(File settingsPath) {
        var pathConfig = PathConfig.createDefault(settingsPath);
        final var dataPath = pathConfig.dataPath();
        final var files = dataPath.listFiles();

        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .toList();
    }

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
