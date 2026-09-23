package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberlessGroupTest {

    @TempDir
    Path directory;

    @Test
    void findsOurGroupMembershipByAciWhenPniIsAbsent() throws Exception {
        final var aci = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
        final var otherAci = ACI.parseOrThrow("22222222-2222-4222-8222-222222222222");
        try (final var account = SignalAccount.createLinkedAccount(directory.toFile(),
                "account",
                ServiceEnvironment.STAGING,
                Settings.DEFAULT); final var context = new Context(account, null, null, null, null, null)) {
            account.setProvisioningData(null,
                    aci,
                    null,
                    "test-password",
                    new byte[]{1},
                    KeyUtils.generateIdentityKeyPair(),
                    null,
                    KeyUtils.createProfileKey(),
                    null,
                    new byte[32],
                    null);
            final var otherMember = new DecryptedMember.Builder().aciBytes(otherAci.toByteString())
                    .joinedAtRevision(1)
                    .build();
            final var selfMember = new DecryptedMember.Builder().aciBytes(aci.toByteString())
                    .joinedAtRevision(4)
                    .build();
            final var group = new DecryptedGroup.Builder().revision(9)
                    .members(List.of(otherMember, selfMember))
                    .build();
            assertEquals(4, context.getGroupV2Helper().findRevisionWeWereAdded(group));
            assertEquals(9,
                    context.getGroupV2Helper()
                            .findRevisionWeWereAdded(group.newBuilder().members(List.of(otherMember)).build()));
        }
    }
}
