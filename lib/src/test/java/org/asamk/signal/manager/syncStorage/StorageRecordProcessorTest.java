package org.asamk.signal.manager.syncStorage;

import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile;

import java.util.UUID;

import okio.ByteString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRecordProcessorTest {

    @Test
    void splitsOnlyUnregisteredAciOnlyRecords() {
        final var aci = ACI.from(UUID.randomUUID());
        final var pni = PNI.from(UUID.randomUUID());

        assertTrue(ContactRecordProcessor.shouldSplitForStorageSync(1, aci, null, ""));
        assertFalse(ContactRecordProcessor.shouldSplitForStorageSync(0, aci, null, ""));
        assertFalse(ContactRecordProcessor.shouldSplitForStorageSync(1, null, null, ""));
        assertFalse(ContactRecordProcessor.shouldSplitForStorageSync(1, aci, pni, ""));
        assertFalse(ContactRecordProcessor.shouldSplitForStorageSync(1, aci, null, "+12025550123"));
    }

    @Test
    void linkedDeviceUsesRemoteIdentityKeyForUnrepairableConflict() {
        assertTrue(ContactRecordProcessor.shouldUseRemoteIdentityKey(false, false, 33, 33, 0, true));
        assertFalse(ContactRecordProcessor.shouldUseRemoteIdentityKey(true, false, 33, 33, 0, true));
        assertFalse(ContactRecordProcessor.shouldUseRemoteIdentityKey(false, false, 33, 33, 0, false));
    }

    @Test
    void notificationProfileMergePrefersRemoteExceptOlderLocalDeletion() {
        final var id = ByteString.of(new byte[]{1, 2, 3});
        final var remote = new SignalNotificationProfileRecord(StorageId.forNotificationProfile(new byte[16]),
                new NotificationProfile.Builder().id(id).name("Remote").deletedAtTimestampMs(0).build());
        final var local = new SignalNotificationProfileRecord(StorageId.forNotificationProfile(new byte[16]),
                new NotificationProfile.Builder().id(id).name("Local").deletedAtTimestampMs(0).build());
        final var processor = new NotificationProfileRecordProcessor(null, null);

        assertEquals(remote, processor.merge(remote, local));

        final var remoteDeleted = new SignalNotificationProfileRecord(remote.getId(),
                remote.getProto().newBuilder().deletedAtTimestampMs(200).build());
        final var localDeleted = new SignalNotificationProfileRecord(local.getId(),
                local.getProto().newBuilder().deletedAtTimestampMs(100).build());
        assertEquals(localDeleted, processor.merge(remoteDeleted, localDeleted));
        assertEquals(remoteDeleted, processor.merge(remoteDeleted, local));

        assertTrue(processor.isInvalid(new SignalNotificationProfileRecord(remote.getId(),
                new NotificationProfile.Builder().name("No id").build())));
        assertFalse(processor.isInvalid(remote));
    }

    @Test
    void keepsOlderLocalStickerDeletion() {
        assertTrue(StickerPackRecordProcessor.shouldKeepLocalDeletion(200, 100));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(100, 200));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(200, 0));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(0, 100));
    }
}