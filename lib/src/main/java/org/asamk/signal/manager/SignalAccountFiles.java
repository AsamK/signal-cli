package org.asamk.signal.manager;

import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.internal.AccountFileUpdaterImpl;
import org.asamk.signal.manager.internal.ManagerImpl;
import org.asamk.signal.manager.internal.MultiAccountManagerImpl;
import org.asamk.signal.manager.internal.PathConfig;
import org.asamk.signal.manager.internal.ProvisioningManagerImpl;
import org.asamk.signal.manager.internal.RegistrationManagerImpl;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.core.models.ServiceId.ACI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SignalAccountFiles {

    private static final Logger logger = LoggerFactory.getLogger(MultiAccountManager.class);

    private final PathConfig pathConfig;
    private final ServiceEnvironment serviceEnvironment;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final Settings settings;
    private final AccountsStore accountsStore;

    public SignalAccountFiles(
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment,
            final String userAgent,
            final Settings settings
    ) throws IOException {
        this.pathConfig = PathConfig.createDefault(settingsPath);
        this.serviceEnvironment = serviceEnvironment;
        this.serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(this.serviceEnvironment, userAgent);
        this.userAgent = userAgent;
        this.settings = settings;
        this.accountsStore = new AccountsStore(pathConfig.dataPath(), serviceEnvironment, accountPath -> {
            if (accountPath == null || !SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
                return null;
            }

            try {
                return SignalAccount.load(pathConfig.dataPath(), accountPath, false, settings);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public Set<String> getAllLocalAccountNumbers() throws IOException {
        return accountsStore.getAllNumbers();
    }

    public Set<String> getAllLocalAccountIdentifiers() throws IOException {
        return accountsStore.getAllAccounts()
                .stream()
                .map(a -> a.number() != null ? a.number() : a.uuid())
                .collect(Collectors.toSet());
    }

    public MultiAccountManager initMultiAccountManager() throws IOException {
        final var managerPairs = accountsStore.getAllAccounts().parallelStream().map(a -> {
            final var identifier = a.number() != null ? a.number() : a.uuid();
            try {
                final var manager = a.number() != null
                        ? initManagerByNumber(a.number(), a.path())
                        : initManagerByAci(ACI.parseOrThrow(a.uuid()), a.path());
                return new Pair<Manager, Throwable>(manager, null);
            } catch (NotRegisteredException e) {
                logger.warn("Ignoring {}: {} ({})", identifier, e.getMessage(), e.getClass().getSimpleName());
                return null;
            } catch (AccountCheckException | IOException e) {
                logger.error("Failed to load {}: {} ({})", identifier, e.getMessage(), e.getClass().getSimpleName());
                return new Pair<Manager, Throwable>(null, e);
            }
        }).filter(Objects::nonNull).toList();

        for (final var pair : managerPairs) {
            if (pair.second() instanceof IOException e) {
                throw e;
            }
        }

        final var managers = managerPairs.stream()
                .filter(p -> p != null && p.first() != null)
                .map(Pair::first)
                .toList();
        return new MultiAccountManagerImpl(managers, this);
    }

    public Manager initManagerByNumber(String number) throws IOException, NotRegisteredException, AccountCheckException {
        final var accountPath = accountsStore.getPathByNumber(number);
        return this.initManagerByNumber(number, accountPath);
    }

    public Manager initManagerByAci(String aciStr) throws IOException, NotRegisteredException, AccountCheckException {
        final var aci = ACI.parseOrThrow(aciStr);
        final var accountPath = accountsStore.getPathByAci(aci);
        return this.initManagerByAci(aci, accountPath);
    }

    private Manager initManagerByNumber(
            String number,
            String accountPath
    ) throws IOException, NotRegisteredException, AccountCheckException {
        final var account = loadAccount(accountPath);
        if (!number.equals(account.getNumber())) {
            account.close();
            throw new IOException("Number in account file doesn't match expected number: " + account.getNumber());
        }

        return initManagerFromAccount(number, accountPath, account);
    }

    private SignalAccount loadAccount(final String accountPath) throws NotRegisteredException, IOException {
        if (accountPath == null) {
            throw new NotRegisteredException();
        }
        if (!SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            throw new NotRegisteredException();
        }

        return SignalAccount.load(pathConfig.dataPath(), accountPath, true, settings);
    }

    private Manager initManagerByAci(
            ACI aci,
            String accountPath
    ) throws IOException, NotRegisteredException, AccountCheckException {
        final var account = loadAccount(accountPath);
        if (!aci.equals(account.getAci())) {
            account.close();
            throw new IOException("ACI in account file doesn't match expected ACI: " + account.getAci());
        }

        return initManagerFromAccount(aci.toString(), accountPath, account);
    }

    private ManagerImpl initManagerFromAccount(
            final String identifier,
            final String accountPath,
            final SignalAccount account
    ) throws NotRegisteredException, IOException, AccountCheckException {
        if (!account.isRegistered()) {
            account.close();
            throw new NotRegisteredException();
        }

        if (account.getServiceEnvironment() != null && account.getServiceEnvironment() != serviceEnvironment) {
            throw new IOException("Account is registered in another environment: " + account.getServiceEnvironment());
        }

        account.initDatabase();

        final var manager = new ManagerImpl(account,
                pathConfig,
                new AccountFileUpdaterImpl(accountsStore, accountPath),
                serviceEnvironmentConfig,
                userAgent);

        try {
            manager.checkAccountState();
        } catch (DeprecatedVersionException e) {
            manager.close();
            throw new IOException("signal-cli version is too old for the Signal-Server, please update.");
        } catch (IOException e) {
            manager.close();
            throw new AccountCheckException("Error while checking account " + identifier + ": " + e.getMessage(), e);
        }

        if (account.getServiceEnvironment() == null) {
            account.setServiceEnvironment(serviceEnvironment);
            accountsStore.updateAccount(accountPath, account.getNumber(), account.getAci());
        }

        return manager;
    }

    public ProvisioningManager initProvisioningManager() {
        return initProvisioningManager(null);
    }

    public ProvisioningManager initProvisioningManager(Consumer<Manager> newManagerListener) {
        return new ProvisioningManagerImpl(pathConfig,
                serviceEnvironmentConfig,
                userAgent,
                newManagerListener,
                accountsStore);
    }

    public RegistrationManager initRegistrationManager(String number) throws IOException {
        return initRegistrationManager(number, null);
    }

    public RegistrationManager initRegistrationManager(
            String number,
            Consumer<Manager> newManagerListener
    ) throws IOException {
        final var aci = ACI.parseOrNull(number);
        if (aci != null) {
            return initRegistrationManager(aci, newManagerListener);
        }

        final var accountPath = accountsStore.getPathByNumber(number);
        if (accountPath == null || !SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            final var newAccountPath = accountPath == null ? accountsStore.addAccount(number, null) : accountPath;
            var aciIdentityKey = KeyUtils.generateIdentityKeyPair();
            var pniIdentityKey = KeyUtils.generateIdentityKeyPair();

            var profileKey = KeyUtils.createProfileKey();
            var account = SignalAccount.create(pathConfig.dataPath(),
                    newAccountPath,
                    number,
                    serviceEnvironment,
                    aciIdentityKey,
                    pniIdentityKey,
                    profileKey,
                    settings);
            account.initDatabase();

            return new RegistrationManagerImpl(account,
                    pathConfig,
                    serviceEnvironmentConfig,
                    userAgent,
                    newManagerListener,
                    new AccountFileUpdaterImpl(accountsStore, newAccountPath));
        }

        var account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, settings);
        if (!number.equals(account.getNumber())) {
            account.close();
            throw new IOException("Number in account file doesn't match expected number: " + account.getNumber());
        }
        account.initDatabase();

        return new RegistrationManagerImpl(account,
                pathConfig,
                serviceEnvironmentConfig,
                userAgent,
                newManagerListener,
                new AccountFileUpdaterImpl(accountsStore, accountPath));
    }

    private RegistrationManager initRegistrationManager(
            final ACI aci,
            final Consumer<Manager> newManagerListener
    ) throws IOException {
        final var accountPath = accountsStore.getPathByAci(aci);
        if (accountPath == null || !SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            final var newAccountPath = accountPath == null ? accountsStore.addAccount(null, aci) : accountPath;
            final var account = SignalAccount.create(pathConfig.dataPath(),
                    newAccountPath,
                    null,
                    aci,
                    serviceEnvironment,
                    KeyUtils.generateIdentityKeyPair(),
                    KeyUtils.generateIdentityKeyPair(),
                    KeyUtils.createProfileKey(),
                    settings);
            account.initDatabase();
            return new RegistrationManagerImpl(account,
                    pathConfig,
                    serviceEnvironmentConfig,
                    userAgent,
                    newManagerListener,
                    new AccountFileUpdaterImpl(accountsStore, newAccountPath));
        }

        final var account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, settings);
        if (!aci.equals(account.getAci())) {
            account.close();
            throw new IOException("ACI in account file doesn't match expected ACI: " + account.getAci());
        }
        account.initDatabase();
        return new RegistrationManagerImpl(account,
                pathConfig,
                serviceEnvironmentConfig,
                userAgent,
                newManagerListener,
                new AccountFileUpdaterImpl(accountsStore, accountPath));
    }
}
