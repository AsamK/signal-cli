package org.asamk.signal.manager.storage.accounts;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.signal.core.models.ServiceId.ACI;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsStoreTest {

    private static final ACI OLD_ACI = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
    private static final ACI NEW_ACI = ACI.parseOrThrow("22222222-2222-4222-8222-222222222222");

    @TempDir
    Path directory;

    @Test
    void discoversNumberlessAccountsByAciAndKeepsEnvironmentsSeparate() throws Exception {
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> null);
        final var aci = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
        final var path = store.addAccount(null, aci);
        store.addAccount("+12025550123", null);

        final var reopened = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, ignored -> null);
        assertEquals(2, reopened.getAllAccounts().size());
        assertEquals(path, reopened.getPathByAci(aci));
        assertNull(reopened.getPathByNumber(null));
        assertEquals(Set.of("+12025550123"), reopened.getAllNumbers());
        assertTrue(new AccountsStore(directory.toFile(), ServiceEnvironment.LIVE, ignored -> null).getAllAccounts()
                .isEmpty());
    }

    @Test
    void updatingAnAciDoesNotLeaveDuplicateNumberlessAccounts() throws Exception {
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> null);
        final var aci = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
        final var oldPath = store.addAccount(null, aci);
        Files.createFile(directory.resolve(oldPath));
        final var newPath = store.addAccount(null, null);
        store.updateAccount(newPath, null, aci);

        assertEquals(newPath, store.getPathByAci(aci));
        assertEquals(1, store.getAllAccounts().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacingANumberDoesNotRediscoverTheOldAccountAsNumberless(final boolean update) throws Exception {
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> null);
        final var oldPath = store.addAccount("+12025550123", OLD_ACI);
        Files.createFile(directory.resolve(oldPath));
        final String newPath;
        if (update) {
            newPath = store.addAccount("+12025550124", NEW_ACI);
            store.updateAccount(newPath, "+12025550123", NEW_ACI);
        } else {
            newPath = store.addAccount("+12025550123", NEW_ACI);
        }

        final var reopened = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> {
            throw new AssertionError("New entries should not require loading account state for discovery");
        });
        assertEquals(Set.of(newPath), getAccountPaths(reopened));
        assertEquals(newPath, reopened.getPathByNumber("+12025550123"));
        assertEquals(oldPath, reopened.getPathByAci(OLD_ACI));

        // Only an explicit account update should turn the old entry into a numberless account.
        reopened.updateAccount(oldPath, null, OLD_ACI);
        assertEquals(Set.of(oldPath, newPath), getAccountPaths(reopened));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void legacyIndexesDistinguishNumberlessAccountsFromSupersededNumberedAccounts(final int version) throws Exception {
        createSavedAccount("old", "+12025550123", OLD_ACI);
        createSavedAccount("numberless", null, NEW_ACI);
        final var legacyIndex = """
                                {
                                  "version": %d,
                                  "accounts": [
                                    {"path": "old", "environment": %s, "number": null, "uuid": "%s"},
                                    {"path": "numberless", "environment": %s, "number": null, "uuid": "%s"},
                                    {"path": "current", "environment": "STAGING", "number": "+12025550123", "uuid": null}
                                  ]
                                }
                                """.formatted(version,
                version == 1 ? "null" : "\"STAGING\"",
                OLD_ACI,
                version == 1 ? "null" : "\"STAGING\"",
                NEW_ACI);
        Files.writeString(directory.resolve("accounts.json"), legacyIndex);

        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, this::loadAccountOrNull);
        assertEquals(Set.of("numberless", "current"), getAccountPaths(store));
        assertEquals("old", store.getPathByAci(OLD_ACI));
        assertEquals("numberless", store.getPathByAci(NEW_ACI));
        assertEquals(Set.of("+12025550123"), store.getAllNumbers());

        final var reopened = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> {
            throw new AssertionError("Resolved entries should not require loading account state again");
        });
        assertEquals(Set.of("numberless", "current"), getAccountPaths(reopened));
        assertTrue(new AccountsStore(directory.toFile(),
                ServiceEnvironment.LIVE,
                this::loadAccountOrNull).getAllAccounts().isEmpty());
    }

    @Test
    void retriesLockedLegacyAccountsAndPersistsSuccessfulClassification() throws Exception {
        createSavedAccount("numberless", null, NEW_ACI);
        writeLegacyNumberlessEntry(NEW_ACI);
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, this::loadAccountOrNull);

        try (final var account = SignalAccount.load(directory.toFile(), "numberless", false, Settings.DEFAULT)) {
            assertTrue(store.getAllAccounts().isEmpty());
            assertEquals("numberless", store.getPathByAci(NEW_ACI));
            final var storage = Utils.createStorageObjectMapper()
                    .readValue(directory.resolve("accounts.json").toFile(), AccountsStorage.class);
            assertNull(storage.accounts().getFirst().numberless());
        }

        assertEquals(Set.of("numberless"), getAccountPaths(store));
        try (final var account = SignalAccount.load(directory.toFile(), "numberless", false, Settings.DEFAULT)) {
            final var reopened = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> {
                throw new AssertionError("A classified account should remain discoverable while locked");
            });
            assertEquals(Set.of("numberless"), getAccountPaths(reopened));
        }
    }

    @Test
    void retriesLegacyAccountsAfterMissingStateIsRestored() throws Exception {
        writeLegacyNumberlessEntry(NEW_ACI);
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, this::loadAccountOrNull);
        assertTrue(store.getAllAccounts().isEmpty());
        assertEquals("numberless", store.getPathByAci(NEW_ACI));

        createSavedAccount("numberless", null, NEW_ACI);
        assertEquals(Set.of("numberless"), getAccountPaths(store));
    }

    @Test
    void doesNotDiscoverLegacyEntriesWithAMismatchedAci() throws Exception {
        createSavedAccount("numberless", null, NEW_ACI);
        writeLegacyNumberlessEntry(OLD_ACI);
        final var store = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, this::loadAccountOrNull);
        assertTrue(store.getAllAccounts().isEmpty());
        assertEquals("numberless", store.getPathByAci(OLD_ACI));
    }

    private Set<String> getAccountPaths(final AccountsStore store) throws IOException {
        return store.getAllAccounts().stream().map(AccountsStorage.Account::path).collect(Collectors.toSet());
    }

    private void writeLegacyNumberlessEntry(final ACI aci) throws IOException {
        final var legacyIndex = """
                                {
                                  "version": 2,
                                  "accounts": [
                                    {"path": "numberless", "environment": "STAGING", "number": null, "uuid": "%s"}
                                  ]
                                }
                                """.formatted(aci);
        Files.writeString(directory.resolve("accounts.json"), legacyIndex);
    }

    private void createSavedAccount(final String path, final String number, final ACI aci) throws IOException {
        // Account creation needs a number until the ACI has been assigned.
        try (final var account = SignalAccount.create(directory.toFile(),
                path,
                "+12025550123",
                ServiceEnvironment.STAGING,
                KeyUtils.generateIdentityKeyPair(),
                KeyUtils.generateIdentityKeyPair(),
                KeyUtils.createProfileKey(),
                Settings.DEFAULT)) {
            account.setAci(aci);
            account.setNumber(number);
        }
    }

    private SignalAccount loadAccountOrNull(final String path) {
        if (!SignalAccount.accountFileExists(directory.toFile(), path)) {
            return null;
        }
        try {
            return SignalAccount.load(directory.toFile(), path, false, Settings.DEFAULT);
        } catch (IOException | OverlappingFileLockException e) {
            return null;
        }
    }
}
