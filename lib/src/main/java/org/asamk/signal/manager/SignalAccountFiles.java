package org.asamk.signal.manager;

import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class SignalAccountFiles {

    private static final Logger logger = LoggerFactory.getLogger(MultiAccountManager.class);

    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final TrustNewIdentity trustNewIdentity;
    private final AccountsStore accountsStore;

    public SignalAccountFiles(
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment,
            final String userAgent,
            final TrustNewIdentity trustNewIdentity
    ) throws IOException {
        this.pathConfig = PathConfig.createDefault(settingsPath);
        this.serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);
        this.userAgent = userAgent;
        this.trustNewIdentity = trustNewIdentity;
        this.accountsStore = new AccountsStore(pathConfig.dataPath());
    }

    public Set<String> getAllLocalAccountNumbers() {
        return accountsStore.getAllNumbers();
    }

    public MultiAccountManager initMultiAccountManager() {
        final var managers = accountsStore.getAllAccounts().parallelStream().map(a -> {
            try {
                return initManager(a.path());
            } catch (NotRegisteredException | IOException | AccountCheckException e) {
                logger.warn("Ignoring {}: {} ({})", a.number(), e.getMessage(), e.getClass().getSimpleName());
                return null;
            }
        }).filter(Objects::nonNull).toList();

        return new MultiAccountManagerImpl(managers, this);
    }

    public Manager initManager(String number) throws IOException, NotRegisteredException, AccountCheckException {
        final var accountPath = accountsStore.getPathByNumber(number);
        return this.initManager(number, accountPath);
    }

    private Manager initManager(
            String number, String accountPath
    ) throws IOException, NotRegisteredException, AccountCheckException {
        if (accountPath == null) {
            throw new NotRegisteredException();
        }
        if (!SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            throw new NotRegisteredException();
        }

        var account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, trustNewIdentity);
        if (!number.equals(account.getNumber())) {
            account.close();
            throw new IOException("Number in account file doesn't match expected number: " + account.getNumber());
        }

        if (!account.isRegistered()) {
            account.close();
            throw new NotRegisteredException();
        }

        account.initDatabase();

        final var manager = new ManagerImpl(account,
                pathConfig,
                (newNumber, newAci) -> accountsStore.updateAccount(accountPath, newNumber, newAci),
                serviceEnvironmentConfig,
                userAgent);

        try {
            manager.checkAccountState();
        } catch (IOException e) {
            manager.close();
            throw new AccountCheckException("Error while checking account " + number + ": " + e.getMessage(), e);
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
            String number, Consumer<Manager> newManagerListener
    ) throws IOException {
        final var accountPath = accountsStore.getPathByNumber(number);
        if (accountPath == null || !SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            final var newAccountPath = accountPath == null ? accountsStore.addAccount(number, null) : accountPath;
            var identityKey = KeyUtils.generateIdentityKeyPair();
            var registrationId = KeyHelper.generateRegistrationId(false);

            var profileKey = KeyUtils.createProfileKey();
            var account = SignalAccount.create(pathConfig.dataPath(),
                    newAccountPath,
                    number,
                    identityKey,
                    registrationId,
                    profileKey,
                    trustNewIdentity);

            return new RegistrationManagerImpl(account,
                    pathConfig,
                    serviceEnvironmentConfig,
                    userAgent,
                    newManagerListener,
                    (newNumber, newAci) -> accountsStore.updateAccount(newAccountPath, newNumber, newAci));
        }

        var account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, trustNewIdentity);
        if (!number.equals(account.getNumber())) {
            account.close();
            throw new IOException("Number in account file doesn't match expected number: " + account.getNumber());
        }

        return new RegistrationManagerImpl(account,
                pathConfig,
                serviceEnvironmentConfig,
                userAgent,
                newManagerListener,
                (newNumber, newAci) -> accountsStore.updateAccount(accountPath, newNumber, newAci));
    }
}
