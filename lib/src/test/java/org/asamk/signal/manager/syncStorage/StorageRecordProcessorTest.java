package org.asamk.signal.manager.syncStorage;

import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;

import java.util.UUID;

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
    void keepsOlderLocalStickerDeletion() {
        assertTrue(StickerPackRecordProcessor.shouldKeepLocalDeletion(200, 100));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(100, 200));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(200, 0));
        assertFalse(StickerPackRecordProcessor.shouldKeepLocalDeletion(0, 100));
    }
}