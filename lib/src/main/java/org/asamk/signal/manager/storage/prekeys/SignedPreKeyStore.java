package org.asamk.signal.manager.storage.prekeys;

import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SignedPreKeyStore implements org.whispersystems.libsignal.state.SignedPreKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(SignedPreKeyStore.class);

    private final File signedPreKeysPath;

    public SignedPreKeyStore(final File signedPreKeysPath) {
        this.signedPreKeysPath = signedPreKeysPath;
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        final var file = getSignedPreKeyFile(signedPreKeyId);

        if (!file.exists()) {
            throw new InvalidKeyIdException("No such signed pre key record!");
        }
        return loadSignedPreKeyRecord(file);
    }

    final Pattern signedPreKeyFileNamePattern = Pattern.compile("([0-9]+)");

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        final var files = signedPreKeysPath.listFiles();
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(f -> signedPreKeyFileNamePattern.matcher(f.getName()).matches())
                .map(this::loadSignedPreKeyRecord)
                .collect(Collectors.toList());
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        final var file = getSignedPreKeyFile(signedPreKeyId);
        try {
            try (var outputStream = new FileOutputStream(file)) {
                outputStream.write(record.serialize());
            }
        } catch (IOException e) {
            logger.warn("Failed to store signed pre key, trying to delete file and retry: {}", e.getMessage());
            try {
                Files.delete(file.toPath());
                try (var outputStream = new FileOutputStream(file)) {
                    outputStream.write(record.serialize());
                }
            } catch (IOException e2) {
                logger.error("Failed to store signed pre key file {}: {}", file, e2.getMessage());
            }
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        final var file = getSignedPreKeyFile(signedPreKeyId);

        return file.exists();
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        final var file = getSignedPreKeyFile(signedPreKeyId);

        if (!file.exists()) {
            return;
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete signed pre key file {}: {}", file, e.getMessage());
        }
    }

    public void removeAllSignedPreKeys() {
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
    }

    private File getSignedPreKeyFile(int signedPreKeyId) {
        try {
            IOUtils.createPrivateDirectories(signedPreKeysPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create signed pre keys path", e);
        }
        return new File(signedPreKeysPath, String.valueOf(signedPreKeyId));
    }

    private SignedPreKeyRecord loadSignedPreKeyRecord(final File file) {
        try (var inputStream = new FileInputStream(file)) {
            return new SignedPreKeyRecord(inputStream.readAllBytes());
        } catch (IOException e) {
            logger.error("Failed to load signed pre key: {}", e.getMessage());
            throw new AssertionError(e);
        }
    }
}
