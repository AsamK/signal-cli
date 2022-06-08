package org.asamk.signal.manager.storage.prekeys;

import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Pattern;

public class LegacySignedPreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacySignedPreKeyStore.class);
    static final Pattern signedPreKeyFileNamePattern = Pattern.compile("(\\d+)");

    public static void migrate(File signedPreKeysPath, SignedPreKeyStore signedPreKeyStore) {
        final var files = signedPreKeysPath.listFiles();
        if (files == null) {
            return;
        }
        final var signedPreKeyRecords = Arrays.stream(files)
                .filter(f -> signedPreKeyFileNamePattern.matcher(f.getName()).matches())
                .map(LegacySignedPreKeyStore::loadSignedPreKeyRecord)
                .toList();
        signedPreKeyStore.addLegacySignedPreKeys(signedPreKeyRecords);
        removeAllSignedPreKeys(signedPreKeysPath);
    }

    private static void removeAllSignedPreKeys(File signedPreKeysPath) {
        final var files = signedPreKeysPath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete signed pre key file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(signedPreKeysPath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete signed pre key directory {}: {}", signedPreKeysPath, e.getMessage());
        }
    }

    private static SignedPreKeyRecord loadSignedPreKeyRecord(final File file) {
        try (var inputStream = new FileInputStream(file)) {
            return new SignedPreKeyRecord(inputStream.readAllBytes());
        } catch (IOException | InvalidMessageException e) {
            logger.error("Failed to load signed pre key: {}", e.getMessage());
            throw new AssertionError(e);
        }
    }
}
