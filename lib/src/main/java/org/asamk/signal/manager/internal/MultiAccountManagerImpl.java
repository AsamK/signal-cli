package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.SignalAccountFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final SignalAccountFiles signalAccountFiles;

    public MultiAccountManagerImpl(final Collection<Manager> managers, final SignalAccountFiles signalAccountFiles) {
        this.signalAccountFiles = signalAccountFiles;
        this.managers.addAll(managers);
        managers.forEach(m -> m.addClosedListener(() -> this.removeManager(m)));
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
    public Manager getManager(final String number) {
        synchronized (managers) {
            return managers.stream().filter(m -> m.getSelfNumber().equals(number)).findFirst().orElse(null);
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
        return signalAccountFiles.initProvisioningManager(this::addManager);
    }

    @Override
    public RegistrationManager getNewRegistrationManager(String number) throws IOException {
        return signalAccountFiles.initRegistrationManager(number, this::addManager);
    }

    @Override
    public void close() {
        synchronized (managers) {
            for (var m : new ArrayList<>(managers)) {
                m.close();
            }
            managers.clear();
        }
    }
}
