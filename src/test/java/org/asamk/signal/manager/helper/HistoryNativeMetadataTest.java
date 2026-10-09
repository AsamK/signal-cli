package org.asamk.signal.manager.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HistoryNativeMetadataTest {

    @TempDir
    Path directory;

    @Test
    void createsAndReadsPendingHistoryState() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var backupKey = new byte[]{1, 2, 3, 4};

        final var created = store.createPending("11111111-1111-4111-8111-111111111111", backupKey);
        final var restored = store.readMetadata();

        assertEquals(created, restored);
        assertArrayEquals(backupKey, restored.decodedBackupKey());
        assertFalse(store.hasArchive(restored));
        assertEquals(HistoryTransferStore.FORMAT_VERSION, restored.version());
        assertFalse(restored.generation().isBlank());
        try (final var files = Files.list(store.directory())) {
            assertEquals(1, files.count());
        }
    }
}
