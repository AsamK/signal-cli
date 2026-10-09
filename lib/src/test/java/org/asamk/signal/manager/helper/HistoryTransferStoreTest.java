package org.asamk.signal.manager.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTransferStoreTest {

    @TempDir
    Path directory;

    @Test
    void persistsPrivateMetadataAndArchiveAndDeletesThemTogether() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var key = new byte[]{1, 2, 3, 4};

        final var pending = store.createPending("11111111-1111-4111-8111-111111111111", key);
        final var located = store.withArchiveLocation(pending, 3, "archive/key");
        store.writeArchive(output -> output.write(new byte[]{5, 6, 7}));

        final var restored = store.readMetadata();
        assertEquals(located, restored);
        assertArrayEquals(key, restored.decodedBackupKey());
        assertTrue(store.hasArchive());
        assertEquals(3, store.archiveLength(restored));
        try (final var archive = store.openArchive(restored)) {
            assertArrayEquals(new byte[]{5, 6, 7}, archive.readAllBytes());
        }

        if (Files.getFileStore(store.directory()).supportsFileAttributeView("posix")) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE),
                    Files.getPosixFilePermissions(store.directory()));
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(store.metadataPath()));
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(store.archivePath()));
        }

        store.delete();
        assertFalse(Files.exists(store.directory()));
    }

    @Test
    void failedFileDownloadDoesNotReplacePublishedArchive() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var metadata = store.createPending("11111111-1111-4111-8111-111111111111",
                new byte[]{1, 2, 3, 4});
        store.writeArchive(metadata, output -> output.write(new byte[]{1, 2, 3}));

        assertThrows(IOException.class, () -> store.writeArchiveFile(metadata, path -> {
            Files.write(path, new byte[]{4, 5});
            throw new IOException("download failed");
        }));

        try (final var archive = store.openArchive(metadata)) {
            assertArrayEquals(new byte[]{1, 2, 3}, archive.readAllBytes());
        }
        try (final var files = Files.list(store.directory())) {
            assertEquals(Set.of(store.archivePath(metadata), store.metadataPath()),
                    files.collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void newTransferGenerationNeverReusesRetainedArchive() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var first = store.createPending("11111111-1111-4111-8111-111111111111",
                new byte[]{1, 2, 3, 4});
        store.writeArchive(first, output -> output.write(new byte[]{5, 6, 7}));
        assertTrue(store.hasArchive(first));

        final var second = store.createPending("11111111-1111-4111-8111-111111111111",
                new byte[]{8, 9, 10, 11});

        assertFalse(store.hasArchive(second));
        assertFalse(Files.exists(store.archivePath(first)));
        assertEquals(second, store.readMetadata());
        assertThrows(IOException.class,
                () -> store.writeArchive(first, output -> output.write(new byte[]{12})));
    }
}
