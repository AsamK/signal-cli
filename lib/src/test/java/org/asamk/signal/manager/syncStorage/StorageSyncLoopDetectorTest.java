package org.asamk.signal.manager.syncStorage;

import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StorageSyncLoopDetectorTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void repeatedPayloadIsDeniedAfterThreeCharges() {
        final var detector = new StorageSyncLoopDetector(() -> true);
        final var write = writeWithInsert(1);

        assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
        for (var index = 0; index < 3; index++) {
            assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
        }

        assertEquals(new StorageSyncLoopDetector.Decision.Denied(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, 3),
                detector.onWriteAttempt(write, true, false, NOW));
    }

    @Test
    void storageIdsAreExcludedFromPayloadFingerprint() {
        final var detector = new StorageSyncLoopDetector(() -> true);

        assertAllowed(detector.onWriteAttempt(writeWithInsert(1), true, false, NOW));
        assertAllowed(detector.onWriteAttempt(writeWithInsert(2), true, false, NOW));
        assertAllowed(detector.onWriteAttempt(writeWithInsert(3), true, false, NOW));
        assertAllowed(detector.onWriteAttempt(writeWithInsert(4), true, false, NOW));

        assertInstanceOf(StorageSyncLoopDetector.Decision.Denied.class,
                detector.onWriteAttempt(writeWithInsert(5), true, false, NOW));
    }

    @Test
    void convergenceClearsTheContentBucket() {
        final var detector = new StorageSyncLoopDetector(() -> true);
        final var write = writeWithInsert(1);
        for (var index = 0; index < 4; index++) {
            assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
        }

        detector.onConverged();

        assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
    }

    @Test
    void failedWritesAreRefunded() {
        final var detector = new StorageSyncLoopDetector(() -> true);
        final var write = writeWithInsert(1);

        for (var index = 0; index < 20; index++) {
            assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
            detector.onWriteFailed(NOW);
        }
    }

    @Test
    void rateBucketLimitsDeleteOnlyWrites() {
        final var detector = new StorageSyncLoopDetector(() -> true);
        final var write = new WriteOperationResult(null, List.of(), List.of(new byte[]{1}));

        for (var index = 0; index < 100; index++) {
            assertAllowed(detector.onWriteAttempt(write, true, false, NOW));
        }

        assertEquals(new StorageSyncLoopDetector.Decision.Denied(StorageSyncLoopDetector.Cause.WRITE_RATE, 100),
                detector.onWriteAttempt(write, true, false, NOW));
    }

    @Test
    void retriesAndSingleDeviceWritesAreExempt() {
        final var retryDetector = new StorageSyncLoopDetector(() -> true);
        final var singleDeviceDetector = new StorageSyncLoopDetector(() -> false);
        final var write = writeWithInsert(1);

        for (var index = 0; index < 20; index++) {
            assertAllowed(retryDetector.onWriteAttempt(write, true, true, NOW));
            assertAllowed(singleDeviceDetector.onWriteAttempt(write, true, false, NOW));
        }
    }

    private static WriteOperationResult writeWithInsert(final int storageIdByte) {
        final var storageId = StorageId.forType(new byte[]{(byte) storageIdByte}, 99);
        final var record = new SignalStorageRecord(storageId, new StorageRecord.Builder().build());
        return new WriteOperationResult(null, List.of(record), List.of());
    }

    private static void assertAllowed(final StorageSyncLoopDetector.Decision decision) {
        assertEquals(StorageSyncLoopDetector.Decision.Allowed.INSTANCE, decision);
    }
}
