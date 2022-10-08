package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacySenderKeyRecordStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacySenderKeyRecordStore.class);

    public static void migrate(
            final File senderKeysPath,
            final RecipientResolver resolver,
            final RecipientAddressResolver addressResolver,
            final SenderKeyStore senderKeyStore
    ) {
        final var files = senderKeysPath.listFiles();
        if (files == null) {
            return;
        }

        final var senderKeys = parseFileNames(files, resolver).stream().map(key -> {
            final var record = loadSenderKeyLocked(key, senderKeysPath);
            final var serviceId = addressResolver.resolveRecipientAddress(key.recipientId).serviceId();
            if (record == null || serviceId.isEmpty()) {
                return null;
            }
            return new Pair<>(new SenderKeyRecordStore.Key(serviceId.get(), key.deviceId, key.distributionId), record);
        }).filter(Objects::nonNull).toList();

        senderKeyStore.addLegacySenderKeys(senderKeys);
        deleteAllSenderKeys(senderKeysPath);
    }

    private static void deleteAllSenderKeys(File senderKeysPath) {
        final var files = senderKeysPath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete sender key file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(senderKeysPath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete sender keys directory {}: {}", senderKeysPath, e.getMessage());
        }
    }

    final static Pattern senderKeyFileNamePattern = Pattern.compile("(\\d+)_(\\d+)_([\\da-z\\-]+)");

    private static List<Key> parseFileNames(final File[] files, final RecipientResolver resolver) {
        return Arrays.stream(files)
                .map(f -> senderKeyFileNamePattern.matcher(f.getName()))
                .filter(Matcher::matches)
                .map(matcher -> {
                    final var recipientId = resolver.resolveRecipient(Long.parseLong(matcher.group(1)));
                    if (recipientId == null) {
                        return null;
                    }
                    return new Key(recipientId, Integer.parseInt(matcher.group(2)), UUID.fromString(matcher.group(3)));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static File getSenderKeyFile(Key key, final File senderKeysPath) {
        return new File(senderKeysPath,
                key.recipientId().id() + "_" + key.deviceId() + "_" + key.distributionId().toString());
    }

    private static SenderKeyRecord loadSenderKeyLocked(final Key key, final File senderKeysPath) {
        final var file = getSenderKeyFile(key, senderKeysPath);
        if (!file.exists()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            return new SenderKeyRecord(inputStream.readAllBytes());
        } catch (IOException | InvalidMessageException e) {
            logger.warn("Failed to load sender key, resetting sender key: {}", e.getMessage());
            return null;
        }
    }

    record Key(RecipientId recipientId, int deviceId, UUID distributionId) {}
}
