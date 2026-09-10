package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.syncStorage.WriteOperationResult;
import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageHelperTest {

    @Test
    void removesKnownTypeLocalOnlyIdsStoredAsUnknown() {
        final var staleStickerPackId = StorageId.forStickerPack(new byte[]{1});
        final var localContactId = StorageId.forContact(new byte[]{2});

        final var result = StorageHelper.findUnknownOnlyLocalStorageIds(List.of(staleStickerPackId, localContactId),
                Set.of(staleStickerPackId));

        assertEquals(List.of(staleStickerPackId), result);
    }

    @Test
    void defersWritesContainingOnlyIdentityConflictsPendingRepair() {
        final var pendingId = StorageId.forContact(new byte[]{1});
        final var otherId = StorageId.forContact(new byte[]{2});
        final var pendingRecord = record(pendingId);
        final var otherRecord = record(otherId);

        assertTrue(StorageHelper.containsOnlyIdentityConflictsPendingRepair(write(List.of(pendingRecord)),
                Set.of(pendingId)));
        assertFalse(StorageHelper.containsOnlyIdentityConflictsPendingRepair(write(List.of(pendingRecord, otherRecord)),
                Set.of(pendingId)));
        assertFalse(StorageHelper.containsOnlyIdentityConflictsPendingRepair(write(List.of()), Set.of(pendingId)));
        assertFalse(StorageHelper.containsOnlyIdentityConflictsPendingRepair(new WriteOperationResult(null,
            List.of(pendingRecord),
            List.of(new byte[]{3})), Set.of(pendingId)));
    }

    private static SignalStorageRecord record(final StorageId id) {
        return new SignalStorageRecord(id, new StorageRecord.Builder().build());
    }

    private static WriteOperationResult write(final List<SignalStorageRecord> inserts) {
        return new WriteOperationResult(null, inserts, List.of());
    }
}
