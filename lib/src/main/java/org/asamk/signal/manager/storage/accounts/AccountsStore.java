package org.asamk.signal.manager.storage.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AccountsStore {

    private static final int MINIMUM_STORAGE_VERSION = 1;
    private static final int CURRENT_STORAGE_VERSION = 2;
    private final static Logger logger = LoggerFactory.getLogger(AccountsStore.class);
    private final ObjectMapper objectMapper = Utils.createStorageObjectMapper();

    private final File dataPath;
    private final String serviceEnvironment;
    private final AccountLoader accountLoader;

    public AccountsStore(
            final File dataPath, final ServiceEnvironment serviceEnvironment, final AccountLoader accountLoader
    ) throws IOException {
        this.dataPath = dataPath;
        this.serviceEnvironment = getServiceEnvironmentString(serviceEnvironment);
        this.accountLoader = accountLoader;
        if (!getAccountsFile().exists()) {
            createInitialAccounts();
        }
    }

    public synchronized Set<String> getAllNumbers() throws IOException {
        return readAccounts().stream()
                .map(AccountsStorage.Account::number)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public synchronized Set<AccountsStorage.Account> getAllAccounts() throws IOException {
        return readAccounts().stream()
                .filter(a -> a.environment() == null || serviceEnvironment.equals(a.environment()))
                .filter(a -> a.number() != null)
                .collect(Collectors.toSet());
    }

    public synchronized String getPathByNumber(String number) throws IOException {
        return readAccounts().stream()
                .filter(a -> a.environment() == null || serviceEnvironment.equals(a.environment()))
                .filter(a -> number.equals(a.number()))
                .map(AccountsStorage.Account::path)
                .findFirst()
                .orElse(null);
    }

    public synchronized String getPathByAci(ACI aci) throws IOException {
        return readAccounts().stream()
                .filter(a -> a.environment() == null || serviceEnvironment.equals(a.environment()))
                .filter(a -> aci.toString().equals(a.uuid()))
                .map(AccountsStorage.Account::path)
                .findFirst()
                .orElse(null);
    }

    public synchronized void updateAccount(String path, String number, ACI aci) {
        updateAccounts(accounts -> accounts.stream().map(a -> {
            if (a.environment() != null && !serviceEnvironment.equals(a.environment())) {
                return a;
            }

            if (path.equals(a.path())) {
                return new AccountsStorage.Account(a.path(),
                        serviceEnvironment,
                        number,
                        aci == null ? null : aci.toString());
            }

            if (number != null && number.equals(a.number())) {
                return new AccountsStorage.Account(a.path(), a.environment(), null, a.uuid());
            }
            if (aci != null && aci.toString().equals(a.toString())) {
                return new AccountsStorage.Account(a.path(), a.environment(), a.number(), null);
            }

            return a;
        }).toList());
    }

    public synchronized String addAccount(String number, ACI aci) {
        final var accountPath = generateNewAccountPath();
        final var account = new AccountsStorage.Account(accountPath,
                serviceEnvironment,
                number,
                aci == null ? null : aci.toString());
        updateAccounts(accounts -> {
            final var existingAccounts = accounts.stream().map(a -> {
                if (a.environment() != null && !serviceEnvironment.equals(a.environment())) {
                    return a;
                }

                if (number != null && number.equals(a.number())) {
                    return new AccountsStorage.Account(a.path(), a.environment(), null, a.uuid());
                }
                if (aci != null && aci.toString().equals(a.uuid())) {
                    return new AccountsStorage.Account(a.path(), a.environment(), a.number(), null);
                }

                return a;
            });
            return Stream.concat(existingAccounts, Stream.of(account)).toList();
        });
        return accountPath;
    }

    public void removeAccount(final String accountPath) {
        updateAccounts(accounts -> accounts.stream().filter(a -> !(
                (a.environment() == null || serviceEnvironment.equals(a.environment())) && a.path().equals(accountPath)
        )).toList());
    }

    private String generateNewAccountPath() {
        return new Random().ints(100000, 1000000)
                .mapToObj(String::valueOf)
                .filter(n -> !new File(dataPath, n).exists() && !new File(dataPath, n + ".d").exists())
                .findFirst()
                .get();
    }

    private File getAccountsFile() {
        return new File(dataPath, "accounts.json");
    }

    private void createInitialAccounts() throws IOException {
        final var legacyAccountPaths = getLegacyAccountPaths();
        final var accountsStorage = new AccountsStorage(legacyAccountPaths.stream()
                .map(number -> new AccountsStorage.Account(number, null, number, null))
                .toList(), CURRENT_STORAGE_VERSION);

        IOUtils.createPrivateDirectories(dataPath);
        var fileName = getAccountsFile();
        if (!fileName.exists()) {
            IOUtils.createPrivateFile(fileName);
        }

        final var pair = openFileChannel(getAccountsFile());
        try (final var fileChannel = pair.first(); final var lock = pair.second()) {
            saveAccountsLocked(fileChannel, accountsStorage);
        }
    }

    private Set<String> getLegacyAccountPaths() {
        final var files = dataPath.listFiles();

        if (files == null) {
            return Set.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .collect(Collectors.toSet());
    }

    private List<AccountsStorage.Account> readAccounts() throws IOException {
        final var pair = openFileChannel(getAccountsFile());
        try (final var fileChannel = pair.first(); final var lock = pair.second()) {
            final var storage = readAccountsLocked(fileChannel);

            var accountsVersion = storage.version() == null ? 1 : storage.version();
            if (accountsVersion > CURRENT_STORAGE_VERSION) {
                throw new IOException("Accounts file was created by a more recent version: " + accountsVersion);
            } else if (accountsVersion < MINIMUM_STORAGE_VERSION) {
                throw new IOException("Accounts file was created by a no longer supported older version: "
                        + accountsVersion);
            } else if (accountsVersion < CURRENT_STORAGE_VERSION) {
                return upgradeAccountsFile(fileChannel, storage, accountsVersion).accounts();
            }
            return storage.accounts();
        }
    }

    private AccountsStorage upgradeAccountsFile(
            final FileChannel fileChannel, final AccountsStorage storage, final int accountsVersion
    ) {
        try {
            List<AccountsStorage.Account> newAccounts = storage.accounts();
            if (accountsVersion < 2) {
                // add environment field
                newAccounts = newAccounts.stream().map(a -> {
                    if (a.environment() != null) {
                        return a;
                    }
                    try (final var account = accountLoader.loadAccountOrNull(a.path())) {
                        if (account == null || account.getServiceEnvironment() == null) {
                            return a;
                        }
                        return new AccountsStorage.Account(a.path(),
                                getServiceEnvironmentString(account.getServiceEnvironment()),
                                a.number(),
                                a.uuid());
                    }
                }).toList();
            }
            final var newStorage = new AccountsStorage(newAccounts, CURRENT_STORAGE_VERSION);
            saveAccountsLocked(fileChannel, newStorage);
            return newStorage;
        } catch (Exception e) {
            logger.warn("Failed to upgrade accounts file", e);
            return storage;
        }
    }

    private void updateAccounts(Function<List<AccountsStorage.Account>, List<AccountsStorage.Account>> updater) {
        try {
            final var pair = openFileChannel(getAccountsFile());
            try (final var fileChannel = pair.first(); final var lock = pair.second()) {
                final var accountsStorage = readAccountsLocked(fileChannel);
                final var newAccountsStorage = updater.apply(accountsStorage.accounts());
                saveAccountsLocked(fileChannel, new AccountsStorage(newAccountsStorage, CURRENT_STORAGE_VERSION));
            }
        } catch (IOException e) {
            logger.error("Failed to update accounts list", e);
        }
    }

    private AccountsStorage readAccountsLocked(FileChannel fileChannel) throws IOException {
        fileChannel.position(0);
        final var inputStream = Channels.newInputStream(fileChannel);
        return objectMapper.readValue(inputStream, AccountsStorage.class);
    }

    private void saveAccountsLocked(FileChannel fileChannel, AccountsStorage accountsStorage) throws IOException {
        try {
            try (var output = new ByteArrayOutputStream()) {
                // Write to memory first to prevent corrupting the file in case of serialization errors
                objectMapper.writeValue(output, accountsStorage);
                var input = new ByteArrayInputStream(output.toByteArray());
                fileChannel.position(0);
                input.transferTo(Channels.newOutputStream(fileChannel));
                fileChannel.truncate(fileChannel.position());
                fileChannel.force(false);
            }
        } catch (Exception e) {
            logger.error("Error saving accounts file: {}", e.getMessage(), e);
        }
    }

    private static Pair<FileChannel, FileLock> openFileChannel(File fileName) throws IOException {
        var fileChannel = new RandomAccessFile(fileName, "rw").getChannel();
        var lock = fileChannel.tryLock();
        if (lock == null) {
            logger.info("Config file is in use by another instance, waiting…");
            lock = fileChannel.lock();
            logger.info("Config file lock acquired.");
        }
        return new Pair<>(fileChannel, lock);
    }

    private String getServiceEnvironmentString(final ServiceEnvironment serviceEnvironment) {
        return switch (serviceEnvironment) {
            case LIVE -> "LIVE";
            case STAGING -> "STAGING";
        };
    }

    public interface AccountLoader {

        SignalAccount loadAccountOrNull(String accountPath);
    }
}
