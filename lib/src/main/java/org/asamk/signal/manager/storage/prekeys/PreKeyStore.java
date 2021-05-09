package org.asamk.signal.manager.storage.prekeys;

import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public class PreKeyStore implements org.whispersystems.libsignal.state.PreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(PreKeyStore.class);

    private final File preKeysPath;

    public PreKeyStore(final File preKeysPath) {
        this.preKeysPath = preKeysPath;
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        final var file = getPreKeyFile(preKeyId);

        if (!file.exists()) {
            throw new InvalidKeyIdException("No such pre key record!");
        }
        try (var inputStream = new FileInputStream(file)) {
            return new PreKeyRecord(inputStream.readAllBytes());
        } catch (IOException e) {
            logger.error("Failed to load pre key: {}", e.getMessage());
            throw new AssertionError(e);
        }
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        final var file = getPreKeyFile(preKeyId);
        try {
            try (var outputStream = new FileOutputStream(file)) {
                outputStream.write(record.serialize());
            }
        } catch (IOException e) {
            logger.warn("Failed to store pre key, trying to delete file and retry: {}", e.getMessage());
            try {
                Files.delete(file.toPath());
                try (var outputStream = new FileOutputStream(file)) {
                    outputStream.write(record.serialize());
                }
            } catch (IOException e2) {
                logger.error("Failed to store pre key file {}: {}", file, e2.getMessage());
            }
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        final var file = getPreKeyFile(preKeyId);

        return file.exists();
    }

    @Override
    public void removePreKey(int preKeyId) {
        final var file = getPreKeyFile(preKeyId);

        if (!file.exists()) {
            return;
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete pre key file {}: {}", file, e.getMessage());
        }
    }

    public void removeAllPreKeys() {
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
    }

    private File getPreKeyFile(int preKeyId) {
        try {
            IOUtils.createPrivateDirectories(preKeysPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create pre keys path", e);
        }
        return new File(preKeysPath, String.valueOf(preKeyId));
    }
}
