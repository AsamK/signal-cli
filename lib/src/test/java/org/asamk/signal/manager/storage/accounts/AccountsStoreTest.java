package org.asamk.signal.manager.storage.accounts;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsStoreTest {

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
}
