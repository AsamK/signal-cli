package org.asamk.signal.manager.helper;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

final class HistoryTransferStore {

    static final int FORMAT_VERSION = 2;

    private static final String DIRECTORY_NAME = "history-transfer";
    private static final String ARCHIVE_PREFIX = "archive-";
    private static final String ARCHIVE_SUFFIX = ".bin";
    private static final String METADATA_NAME = "metadata.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path directory;
    private final Path metadataPath;

    HistoryTransferStore(final SignalAccount account) {
        this(account.getAccountDataPath().toPath().resolve(DIRECTORY_NAME));
    }

    HistoryTransferStore(final Path directory) {
        this.directory = directory;
        this.metadataPath = directory.resolve(METADATA_NAME);
    }

    Metadata createPending(final String aci, final byte[] backupKey) throws IOException {
        ensureDirectory();
        final var staleArchives = archiveFiles();
        final var metadata = new Metadata(FORMAT_VERSION,
                UUID.randomUUID().toString(),
                aci,
                Base64.getEncoder().encodeToString(backupKey),
                null,
                null,
                Instant.now().toEpochMilli());
        writeMetadata(metadata);
        for (final var staleArchive : staleArchives) {
            Files.deleteIfExists(staleArchive);
        }
        return metadata;
    }

    Metadata withArchiveLocation(final Metadata metadata, final int cdn, final String cdnKey) throws IOException {
        final var updated = new Metadata(metadata.version(),
                metadata.generation(),
                metadata.aci(),
                metadata.backupKey(),
                cdn,
                cdnKey,
                metadata.createdAt());
        requireCurrentGeneration(metadata);
        writeMetadata(updated);
        return updated;
    }

    Metadata readMetadata() throws IOException {
        if (!Files.isRegularFile(metadataPath)) {
            throw new IOException("No linked-device history transfer is available");
        }
        final var metadata = objectMapper.readValue(metadataPath.toFile(), Metadata.class);
        if (metadata.version() != FORMAT_VERSION) {
            throw new IOException("Unsupported history transfer metadata version: " + metadata.version());
        }
        return metadata;
    }

    void writeArchive(final ArchiveWriter writer) throws IOException {
        writeArchive(readMetadata(), writer);
    }

    void writeArchive(final Metadata metadata, final ArchiveWriter writer) throws IOException {
        requireCurrentGeneration(metadata);
        ensureDirectory();
        final var temporary = createPrivateTempFile("archive-", ".tmp");
        try {
            try (final var output = Files.newOutputStream(temporary)) {
                writer.write(output);
                output.flush();
            }
            requireCurrentGeneration(metadata);
            moveAtomically(temporary, archivePath(metadata));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void writeArchiveFile(final Metadata metadata, final ArchiveFileWriter writer) throws IOException {
        requireCurrentGeneration(metadata);
        ensureDirectory();
        final var temporary = createPrivateTempFile("archive-", ".tmp");
        try {
            writer.write(temporary);
            requireCurrentGeneration(metadata);
            moveAtomically(temporary, archivePath(metadata));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    boolean hasArchive() throws IOException {
        return hasArchive(readMetadata());
    }

    boolean hasArchive(final Metadata metadata) {
        return Files.isRegularFile(archivePath(metadata));
    }

    long archiveLength(final Metadata metadata) throws IOException {
        return Files.size(archivePath(metadata));
    }

    InputStream openArchive(final Metadata metadata) throws IOException {
        return Files.newInputStream(archivePath(metadata));
    }

    void delete() throws IOException {
        if (Files.isDirectory(directory)) {
            try (final var files = Files.list(directory)) {
                for (final var path : files.toList()) {
                    final var name = path.getFileName().toString();
                    if (name.startsWith(ARCHIVE_PREFIX) && name.endsWith(ARCHIVE_SUFFIX)) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
        Files.deleteIfExists(metadataPath);
        Files.deleteIfExists(directory);
    }

    Path directory() {
        return directory;
    }

    Path archivePath() throws IOException {
        return archivePath(readMetadata());
    }

    Path archivePath(final Metadata metadata) {
        return directory.resolve(ARCHIVE_PREFIX + metadata.generation() + ARCHIVE_SUFFIX);
    }

    Path metadataPath() {
        return metadataPath;
    }

    private void writeMetadata(final Metadata metadata) throws IOException {
        ensureDirectory();
        final var temporary = createPrivateTempFile("metadata-", ".tmp");
        try {
            objectMapper.writeValue(temporary.toFile(), metadata);
            moveAtomically(temporary, metadataPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void ensureDirectory() throws IOException {
        IOUtils.createPrivateDirectories(directory.toFile());
    }

    private void requireCurrentGeneration(final Metadata metadata) throws IOException {
        if (!metadata.generation().equals(readMetadata().generation())) {
            throw new IOException("History transfer state changed while publishing the archive");
        }
    }

    private List<Path> archiveFiles() throws IOException {
        try (final var files = Files.list(directory)) {
            return files.filter(path -> {
                final var name = path.getFileName().toString();
                return name.startsWith(ARCHIVE_PREFIX) && name.endsWith(ARCHIVE_SUFFIX);
            }).toList();
        }
    }

    private Path createPrivateTempFile(final String prefix, final String suffix) throws IOException {
        try {
            return Files.createTempFile(directory,
                    prefix,
                    suffix,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            final var path = Files.createTempFile(directory, prefix, suffix);
            path.toFile().setReadable(false, false);
            path.toFile().setWritable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(true, true);
            return path;
        }
    }

    private static void moveAtomically(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record Metadata(
            int version,
            String generation,
            String aci,
            String backupKey,
            Integer cdn,
            String cdnKey,
            long createdAt
    ) {

        byte[] decodedBackupKey() {
            return Base64.getDecoder().decode(backupKey);
        }
    }

    @FunctionalInterface
    interface ArchiveWriter {

        void write(OutputStream output) throws IOException;
    }

    @FunctionalInterface
    interface ArchiveFileWriter {

        void write(Path path) throws IOException;
    }
}
