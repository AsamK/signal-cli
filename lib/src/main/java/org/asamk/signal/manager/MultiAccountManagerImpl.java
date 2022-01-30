package org.asamk.signal.manager;

import org.asamk.signal.manager.config.ServiceEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class MultiAccountManagerImpl implements MultiAccountManager {

    private final static Logger logger = LoggerFactory.getLogger(MultiAccountManagerImpl.class);

    private final Set<Consumer<Manager>> onManagerAddedHandlers = new HashSet<>();
    private final Set<Consumer<Manager>> onManagerRemovedHandlers = new HashSet<>();
    private final Set<Manager> managers = new HashSet<>();
    private final Map<URI, ProvisioningManager> provisioningManagers = new HashMap<>();
    private final File dataPath;
    private final ServiceEnvironment serviceEnvironment;
    private final String userAgent;

    public MultiAccountManagerImpl(
            final Collection<Manager> managers,
            final File dataPath,
            final ServiceEnvironment serviceEnvironment,
            final String userAgent
    ) {
        this.managers.addAll(managers);
        managers.forEach(m -> m.addClosedListener(() -> this.removeManager(m)));
        this.dataPath = dataPath;
        this.serviceEnvironment = serviceEnvironment;
        this.userAgent = userAgent;
    }

    @Override
    public List<String> getAccountNumbers() {
        synchronized (managers) {
            return managers.stream().map(Manager::getSelfNumber).toList();
        }
    }

    @Override
    public List<Manager> getManagers() {
        synchronized (managers) {
            return new ArrayList<>(managers);
        }
    }

    void addManager(final Manager m) {
        synchronized (managers) {
            if (managers.contains(m)) {
                return;
            }
            managers.add(m);
            m.addClosedListener(() -> this.removeManager(m));
        }
        synchronized (onManagerAddedHandlers) {
            for (final var handler : onManagerAddedHandlers) {
                handler.accept(m);
            }
        }
    }

    @Override
    public void addOnManagerAddedHandler(final Consumer<Manager> handler) {
        synchronized (onManagerAddedHandlers) {
            onManagerAddedHandlers.add(handler);
        }
    }

    void removeManager(final Manager m) {
        synchronized (managers) {
            if (!managers.remove(m)) {
                return;
            }
        }
        synchronized (onManagerRemovedHandlers) {
            for (final var handler : onManagerRemovedHandlers) {
                handler.accept(m);
            }
        }
    }

    @Override
    public void addOnManagerRemovedHandler(final Consumer<Manager> handler) {
        synchronized (onManagerRemovedHandlers) {
            onManagerRemovedHandlers.add(handler);
        }
    }

    @Override
    public Manager getManager(final String account) {
        synchronized (managers) {
            return managers.stream().filter(m -> m.getSelfNumber().equals(account)).findFirst().orElse(null);
        }
    }

    @Override
    public URI getNewProvisioningDeviceLinkUri() throws TimeoutException, IOException {
        final var provisioningManager = getNewProvisioningManager();
        final var deviceLinkUri = provisioningManager.getDeviceLinkUri();
        provisioningManagers.put(deviceLinkUri, provisioningManager);
        return deviceLinkUri;
    }

    @Override
    public ProvisioningManager getProvisioningManagerFor(final URI deviceLinkUri) {
        return provisioningManagers.remove(deviceLinkUri);
    }

    private ProvisioningManager getNewProvisioningManager() {
        return ProvisioningManager.init(dataPath, serviceEnvironment, userAgent, this::addManager);
    }

    @Override
    public RegistrationManager getNewRegistrationManager(String account) throws IOException {
        return RegistrationManager.init(account, dataPath, serviceEnvironment, userAgent, this::addManager);
    }

    @Override
    public void close() {
        synchronized (managers) {
            for (var m : new ArrayList<>(managers)) {
                try {
                    m.close();
                } catch (IOException e) {
                    logger.warn("Cleanup failed", e);
                }
            }
            managers.clear();
        }
    }
}
