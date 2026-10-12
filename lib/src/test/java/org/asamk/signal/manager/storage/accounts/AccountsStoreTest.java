package org.asamk.signal.manager.storage.accounts;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.signal.core.models.ServiceId.ACI;

import java.io.IOException;
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
    void replacingANumberKeepsTheOldAccountAvailableByAci(final boolean update) throws Exception {
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

        final var reopened = new AccountsStore(directory.toFile(), ServiceEnvironment.STAGING, path -> null);
        assertEquals(Set.of(oldPath, newPath), getAccountPaths(reopened));
        assertEquals(newPath, reopened.getPathByNumber("+12025550123"));
        assertEquals(oldPath, reopened.getPathByAci(OLD_ACI));
    }

    private Set<String> getAccountPaths(final AccountsStore store) throws IOException {
        return store.getAllAccounts().stream().map(AccountsStorage.Account::path).collect(Collectors.toSet());
    }

}
