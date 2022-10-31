package org.asamk.signal.manager;

import org.asamk.signal.manager.api.AccountCheckException;
import org.asamk.signal.manager.api.NotRegisteredException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

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

    public MultiAccountManager initMultiAccountManager() throws IOException {
        final var managers = accountsStore.getAllAccounts().parallelStream().map(a -> {
            try {
                return initManager(a.number(), a.path());
            } catch (NotRegisteredException | IOException | AccountCheckException e) {
                logger.warn("Ignoring {}: {} ({})", a.number(), e.getMessage(), e.getClass().getSimpleName());
                return null;
            } catch (Throwable e) {
                logger.error("Failed to load {}: {} ({})", a.number(), e.getMessage(), e.getClass().getSimpleName());
                throw e;
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

        var account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, settings);
        if (!number.equals(account.getNumber())) {
            account.close();
            throw new IOException("Number in account file doesn't match expected number: " + account.getNumber());
        }

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
            throw new AccountCheckException("signal-cli version is too old for the Signal-Server, please update.");
        } catch (IOException e) {
            manager.close();
            throw new AccountCheckException("Error while checking account " + number + ": " + e.getMessage(), e);
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
            String number, Consumer<Manager> newManagerListener
    ) throws IOException {
        final var accountPath = accountsStore.getPathByNumber(number);
        if (accountPath == null || !SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)) {
            final var newAccountPath = accountPath == null ? accountsStore.addAccount(number, null) : accountPath;
            var aciIdentityKey = KeyUtils.generateIdentityKeyPair();
            var pniIdentityKey = KeyUtils.generateIdentityKeyPair();
            var registrationId = KeyHelper.generateRegistrationId(false);
            var pniRegistrationId = KeyHelper.generateRegistrationId(false);

            var profileKey = KeyUtils.createProfileKey();
            var account = SignalAccount.create(pathConfig.dataPath(),
                    newAccountPath,
                    number,
                    serviceEnvironment,
                    aciIdentityKey,
                    pniIdentityKey,
                    registrationId,
                    pniRegistrationId,
                    profileKey,
                    settings);

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

        return new RegistrationManagerImpl(account,
                pathConfig,
                serviceEnvironmentConfig,
                userAgent,
                newManagerListener,
                new AccountFileUpdaterImpl(accountsStore, accountPath));
    }
}
