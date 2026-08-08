package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.storage.recipients.TestRecipientId;
import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup;
import org.whispersystems.signalservice.api.push.DistributionId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupInfoTerminatedTest {

    private static final RecipientResolver UNUSED_RESOLVER = new RecipientResolver() {
        @Override
        public RecipientId resolveRecipient(final RecipientAddress address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecipientId resolveRecipient(final long recipientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecipientId resolveRecipient(final String identifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecipientId resolveRecipient(final ServiceId serviceId) {
            throw new UnsupportedOperationException();
        }
    };

    private static GroupMasterKey masterKey() {
        final var bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 1);
        }
        try {
            return new GroupMasterKey(bytes);
        } catch (InvalidInputException e) {
            throw new AssertionError(e);
        }
    }

    private static GroupInfoV2 groupV2(final DecryptedGroup group) {
        final var masterKey = masterKey();
        return new GroupInfoV2(GroupUtils.getGroupIdV2(masterKey),
                masterKey,
                group,
                DistributionId.create(),
                false,
                false,
                false,
                null,
                UNUSED_RESOLVER);
    }

    @Test
    void v1GroupsAreNeverTerminated() {
        final var group = new GroupInfoV1(GroupId.v1(new byte[16]));
        assertFalse(group.isTerminated());
    }

    @Test
    void v2ReadsTerminatedFlagFromDecryptedGroup() {
        assertTrue(groupV2(new DecryptedGroup.Builder().terminated(true).build()).isTerminated());
        assertFalse(groupV2(new DecryptedGroup.Builder().terminated(false).build()).isTerminated());
        assertFalse(groupV2(new DecryptedGroup.Builder().build()).isTerminated());
    }

    @Test
    void v2IsNotTerminatedWhenGroupStateMissing() {
        final var masterKey = masterKey();
        final var group = new GroupInfoV2(GroupUtils.getGroupIdV2(masterKey), masterKey, UNUSED_RESOLVER);
        assertFalse(group.isTerminated());
    }

    @Test
    void groupApiRecordCarriesTerminatedFromModel() {
        final RecipientId self = TestRecipientId.createTestId(1);

        final var terminated = Group.from(groupV2(new DecryptedGroup.Builder().terminated(true).build()),
                recipientId -> null,
                self);
        assertTrue(terminated.isTerminated());

        final var live = Group.from(groupV2(new DecryptedGroup.Builder().terminated(false).build()),
                recipientId -> null,
                self);
        assertFalse(live.isTerminated());
    }
}
