package org.asamk.signal.manager.storage;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberlessAccountTest {

    @TempDir
    Path directory;

    @Test
    void numberlessLinkedAccountSurvivesReload() throws Exception {
        final var aci = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
        final var identity = KeyUtils.generateIdentityKeyPair();
        final var salt = new byte[32];
        salt[0] = 42;
        try (final var account = SignalAccount.createLinkedAccount(directory.toFile(), "account",
                ServiceEnvironment.STAGING, Settings.DEFAULT)) {
            account.setProvisioningData(null, aci, null, "test-password", new byte[]{1}, identity,
                    null, KeyUtils.createProfileKey(), null, salt, null);
            account.finishLinking(2, KeyUtils.generatePreKeysForType(account.getAccountData(ServiceIdType.ACI)), null);
        }

        try (final var account = SignalAccount.load(directory.toFile(), "account", true, Settings.DEFAULT)) {
            assertTrue(account.isRegistered());
            assertFalse(account.isPrimaryDevice());
            assertEquals(2, account.getDeviceId());
            assertEquals(aci, account.getAci());
            assertNull(account.getNumber());
            assertNull(account.getPni());
            assertNull(account.getPniIdentityKeyPair());
            assertNull(account.getSignalServiceDataStore().pniOrNull());
            assertArrayEquals(identity.serialize(), account.getAciIdentityKeyPair().serialize());
            assertArrayEquals(salt, account.getAuthCredentialSalt());
            assertNull(account.getAccountAttributesV2().getPniRegistrationId());
            assertNull(account.getAccountAttributesV2().getDiscoverableByPhoneNumber());
            assertTrue(account.getAccountAttributesV2().getCapabilities().getOptionalPhoneNumber());
        }
    }
}
