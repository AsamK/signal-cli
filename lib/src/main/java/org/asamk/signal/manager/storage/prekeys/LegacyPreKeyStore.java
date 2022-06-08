package org.asamk.signal.manager.storage.prekeys;

import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Pattern;

public class LegacyPreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacyPreKeyStore.class);
    static final Pattern preKeyFileNamePattern = Pattern.compile("(\\d+)");

    public static void migrate(File preKeysPath, PreKeyStore preKeyStore) {
        final var files = preKeysPath.listFiles();
        if (files == null) {
            return;
        }
        final var preKeyRecords = Arrays.stream(files)
                .filter(f -> preKeyFileNamePattern.matcher(f.getName()).matches())
                .map(LegacyPreKeyStore::loadPreKeyRecord)
                .toList();
        preKeyStore.addLegacyPreKeys(preKeyRecords);
        removeAllPreKeys(preKeysPath);
    }

    private static void removeAllPreKeys(File preKeysPath) {
        final var files = preKeysPath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete pre key file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(preKeysPath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete pre key directory {}: {}", preKeysPath, e.getMessage());
        }
    }

    private static PreKeyRecord loadPreKeyRecord(final File file) {
        try (var inputStream = new FileInputStream(file)) {
            return new PreKeyRecord(inputStream.readAllBytes());
        } catch (IOException | InvalidMessageException e) {
            logger.error("Failed to load pre key: {}", e.getMessage());
            throw new AssertionError(e);
        }
    }
}
