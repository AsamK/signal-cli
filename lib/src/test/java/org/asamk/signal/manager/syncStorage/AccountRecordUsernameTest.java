package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRecordUsernameTest {

    @TempDir
    Path directory;

    private SignalAccount createAccount() throws Exception {
        return SignalAccount.create(directory.toFile(),
                "account",
                "+12025550123",
                ServiceEnvironment.STAGING,
                KeyUtils.generateIdentityKeyPair(),
                KeyUtils.generateIdentityKeyPair(),
                KeyUtils.createProfileKey(),
                Settings.DEFAULT);
    }

    @Test
    void localAccountRecordUsesAccountUsername() throws Exception {
        try (final var account = createAccount()) {
            account.setUsername("alice.42");
            try (final var connection = account.getAccountDatabase().getConnection()) {
                final var self = account.getRecipientStore().getRecipient(connection, account.getSelfRecipientId());
                assertTrue(self.getAddress().username().isEmpty(), "precondition: self recipient has no username");

                final var processor = new AccountRecordProcessor(account, connection, null);
                final var local = processor.getMatching(null).orElseThrow();

                assertEquals("alice.42", local.getProto().username);
            }
        }
    }

    @Test
    void localAccountRecordHasEmptyUsernameWhenUnset() throws Exception {
        try (final var account = createAccount()) {
            try (final var connection = account.getAccountDatabase().getConnection()) {
                final var processor = new AccountRecordProcessor(account, connection, null);
                final var local = processor.getMatching(null).orElseThrow();

                assertEquals("", local.getProto().username);
            }
        }
    }
}
