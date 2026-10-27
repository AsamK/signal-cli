package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMember;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SenderKeyRotationTest {

    private static final ACI SELF = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");
    private static final ACI MEMBER = ACI.parseOrThrow("22222222-2222-4222-8222-222222222222");
    private static final ACI FORMER_MEMBER = ACI.parseOrThrow("33333333-3333-4333-8333-333333333333");
    private static final ACI INVITED = ACI.parseOrThrow("44444444-4444-4444-8444-444444444444");
    private static final ACI OTHER_INVITED = ACI.parseOrThrow("55555555-5555-4555-8555-555555555555");
    private static final int SELF_DEVICE_ID = 2;

    @TempDir
    Path directory;

    @Test
    void rotatesOurSenderKeyWhenItWasSharedWithAFormerMember() throws Exception {
        try (final var account = createAccount(); final var context = createContext(account)) {
            final var distributionId = DistributionId.create();
            final var before = shareOurSenderKey(account, distributionId, MEMBER, FORMER_MEMBER);

            context.getSendHelper()
                    .rotateSenderKeyIfSharedWithNonMembers(distributionId, memberIds(account, SELF, MEMBER));

            assertEquals(Set.of(), account.getSenderKeyStore().getSenderKeySharedWith(distributionId));
            final var after = ourSenderKeySignature(account, distributionId);
            assertFalse(Arrays.equals(before, after), "a new sender key must have been created");
        }
    }

    @Test
    void keepsOurSenderKeyWhileEveryoneItWasSharedWithIsAMember() throws Exception {
        try (final var account = createAccount(); final var context = createContext(account)) {
            final var distributionId = DistributionId.create();
            final var before = shareOurSenderKey(account, distributionId, MEMBER, FORMER_MEMBER);

            context.getSendHelper()
                    .rotateSenderKeyIfSharedWithNonMembers(distributionId,
                            memberIds(account, SELF, MEMBER, FORMER_MEMBER));

            assertEquals(2, account.getSenderKeyStore().getSenderKeySharedWith(distributionId).size());
            assertArrayEquals(before, ourSenderKeySignature(account, distributionId));
        }
    }

    @Test
    void rotatesOurSenderKeyWhenAStoredGroupStateLosesAFullMember() throws Exception {
        try (final var account = createAccount()) {
            final var group = storeGroup(account, groupState(1, List.of(SELF, MEMBER, FORMER_MEMBER), List.of()));
            final var before = shareOurSenderKey(account, group.getDistributionId(), MEMBER, FORMER_MEMBER);
            // As after a decryption error message from the former member: the check before a send no longer sees them
            account.getSenderKeyStore().deleteSharedWith(FORMER_MEMBER);

            group.setGroup(groupState(2, List.of(SELF, MEMBER), List.of()));
            account.getGroupStore().updateGroup(group);

            assertEquals(Set.of(), account.getSenderKeyStore().getSenderKeySharedWith(group.getDistributionId()));
            final var after = ourSenderKeySignature(account, group.getDistributionId());
            assertFalse(Arrays.equals(before, after), "a new sender key must have been created");
        }
    }

    @Test
    void keepsOurSenderKeyWhenOnlyInvitedMembersChange() throws Exception {
        try (final var account = createAccount()) {
            final var group = storeGroup(account,
                    groupState(1, List.of(SELF, MEMBER), List.of(INVITED, OTHER_INVITED)));
            final var before = shareOurSenderKey(account, group.getDistributionId(), MEMBER);

            // One invitation is accepted, the other one is revoked
            group.setGroup(groupState(2, List.of(SELF, MEMBER, INVITED), List.of()));
            account.getGroupStore().updateGroup(group);

            assertEquals(1, account.getSenderKeyStore().getSenderKeySharedWith(group.getDistributionId()).size());
            assertArrayEquals(before, ourSenderKeySignature(account, group.getDistributionId()));
        }
    }

    private SignalAccount createAccount() throws Exception {
        final var account = SignalAccount.createLinkedAccount(directory.toFile(),
                "account",
                ServiceEnvironment.STAGING,
                Settings.DEFAULT);
        account.setProvisioningData(null,
                SELF,
                null,
                "test-password",
                new byte[]{1},
                KeyUtils.generateIdentityKeyPair(),
                null,
                KeyUtils.createProfileKey(),
                null,
                new byte[32],
                null);
        return account;
    }

    private static Context createContext(final SignalAccount account) {
        return new Context(account, null, null, null, null, null);
    }

    private static GroupInfoV2 storeGroup(final SignalAccount account, final DecryptedGroup state) throws Exception {
        final var masterKey = new GroupMasterKey(new byte[32]);
        final var group = new GroupInfoV2(GroupUtils.getGroupIdV2(masterKey),
                masterKey,
                account.getRecipientResolver());
        group.setGroup(state);
        account.getGroupStore().updateGroup(group);
        return group;
    }

    private static DecryptedGroup groupState(final int revision, final List<ACI> members, final List<ACI> invited) {
        return new DecryptedGroup.Builder().revision(revision)
                .members(members.stream()
                        .map(aci -> new DecryptedMember.Builder().aciBytes(aci.toByteString()).build())
                        .toList())
                .pendingMembers(invited.stream()
                        .map(aci -> new DecryptedPendingMember.Builder().serviceIdBytes(aci.toByteString()).build())
                        .toList())
                .build();
    }

    /**
     * Creates our sender key for the distribution id and marks it as shared with the given recipients.
     *
     * @return the public signature key of our sender key
     */
    private static byte[] shareOurSenderKey(
            final SignalAccount account,
            final DistributionId distributionId,
            final ACI... sharedWith
    ) {
        final var signature = ourSenderKeySignature(account, distributionId);
        account.getSenderKeyStore()
                .markSenderKeySharedWith(distributionId,
                        Stream.of(sharedWith).map(aci -> new SignalProtocolAddress(aci.toString(), 1)).toList());
        return signature;
    }

    /**
     * The public signature key of our sender key for the distribution id, creating the key if there is none.
     */
    private static byte[] ourSenderKeySignature(final SignalAccount account, final DistributionId distributionId) {
        final var self = new SignalProtocolAddress(SELF.toString(), SELF_DEVICE_ID);
        return new GroupSessionBuilder(account.getSenderKeyStore()).create(self, distributionId.asUuid())
                .getSignatureKey()
                .serialize();
    }

    private static Set<RecipientId> memberIds(final SignalAccount account, final ACI... members) {
        return Stream.of(members).map(account.getRecipientResolver()::resolveRecipient).collect(Collectors.toSet());
    }
}
