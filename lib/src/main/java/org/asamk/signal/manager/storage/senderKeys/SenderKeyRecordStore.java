package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SenderKeyRecordStore implements org.signal.libsignal.protocol.groups.state.SenderKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(SenderKeyRecordStore.class);

    private final Map<Key, SenderKeyRecord> cachedSenderKeys = new HashMap<>();

    private final File senderKeysPath;

    private final RecipientResolver resolver;

    public SenderKeyRecordStore(
            final File senderKeysPath, final RecipientResolver resolver
    ) {
        this.senderKeysPath = senderKeysPath;
        this.resolver = resolver;
    }

    @Override
    public SenderKeyRecord loadSenderKey(final SignalProtocolAddress address, final UUID distributionId) {
        final var key = getKey(address, distributionId);

        synchronized (cachedSenderKeys) {
            return loadSenderKeyLocked(key);
        }
    }

    @Override
    public void storeSenderKey(
            final SignalProtocolAddress address, final UUID distributionId, final SenderKeyRecord record
    ) {
        final var key = getKey(address, distributionId);

        synchronized (cachedSenderKeys) {
            storeSenderKeyLocked(key, record);
        }
    }

    long getCreateTimeForKey(final RecipientId selfRecipientId, final int selfDeviceId, final UUID distributionId) {
        final var key = getKey(selfRecipientId, selfDeviceId, distributionId);
        final var senderKeyFile = getSenderKeyFile(key);

        if (!senderKeyFile.exists()) {
            return -1;
        }

        return IOUtils.getFileCreateTime(senderKeyFile);
    }

    void deleteSenderKey(final RecipientId recipientId, final UUID distributionId) {
        synchronized (cachedSenderKeys) {
            cachedSenderKeys.clear();
            final var keys = getKeysLocked(recipientId);
            for (var key : keys) {
                if (key.distributionId.equals(distributionId)) {
                    deleteSenderKeyLocked(key);
                }
            }
        }
    }

    void deleteAll() {
        synchronized (cachedSenderKeys) {
            cachedSenderKeys.clear();
            final var files = senderKeysPath.listFiles((_file, s) -> senderKeyFileNamePattern.matcher(s).matches());
            if (files == null) {
                return;
            }

            for (final var file : files) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    logger.error("Failed to delete sender key file {}: {}", file, e.getMessage());
                }
            }
        }
    }

    void deleteAllFor(final RecipientId recipientId) {
        synchronized (cachedSenderKeys) {
            cachedSenderKeys.clear();
            final var keys = getKeysLocked(recipientId);
            for (var key : keys) {
                deleteSenderKeyLocked(key);
            }
        }
    }

    void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        synchronized (cachedSenderKeys) {
            final var keys = getKeysLocked(toBeMergedRecipientId);
            final var otherHasSenderKeys = keys.size() > 0;
            if (!otherHasSenderKeys) {
                return;
            }

            logger.debug("To be merged recipient had sender keys, re-assigning to the new recipient.");
            for (var key : keys) {
                final var toBeMergedSenderKey = loadSenderKeyLocked(key);
                deleteSenderKeyLocked(key);
                if (toBeMergedSenderKey == null) {
                    continue;
                }

                final var newKey = new Key(recipientId, key.deviceId(), key.distributionId);
                final var senderKeyRecord = loadSenderKeyLocked(newKey);
                if (senderKeyRecord != null) {
                    continue;
                }
                storeSenderKeyLocked(newKey, toBeMergedSenderKey);
            }
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }

    private Key getKey(final RecipientId recipientId, int deviceId, final UUID distributionId) {
        return new Key(recipientId, deviceId, distributionId);
    }

    private Key getKey(final SignalProtocolAddress address, final UUID distributionId) {
        final var recipientId = resolveRecipient(address.getName());
        return new Key(recipientId, address.getDeviceId(), distributionId);
    }

    private List<Key> getKeysLocked(RecipientId recipientId) {
        final var files = senderKeysPath.listFiles((_file, s) -> s.startsWith(recipientId.id() + "_"));
        if (files == null) {
            return List.of();
        }
        return parseFileNames(files);
    }

    final Pattern senderKeyFileNamePattern = Pattern.compile("(\\d+)_(\\d+)_([\\da-z\\-]+)");

    private List<Key> parseFileNames(final File[] files) {
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

    private File getSenderKeyFile(Key key) {
        try {
            IOUtils.createPrivateDirectories(senderKeysPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create sender keys path: " + e.getMessage(), e);
        }
        return new File(senderKeysPath,
                key.recipientId().id() + "_" + key.deviceId() + "_" + key.distributionId.toString());
    }

    private SenderKeyRecord loadSenderKeyLocked(final Key key) {
        {
            final var senderKeyRecord = cachedSenderKeys.get(key);
            if (senderKeyRecord != null) {
                return senderKeyRecord;
            }
        }

        final var file = getSenderKeyFile(key);
        if (!file.exists()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            final var senderKeyRecord = new SenderKeyRecord(inputStream.readAllBytes());
            cachedSenderKeys.put(key, senderKeyRecord);
            return senderKeyRecord;
        } catch (IOException | InvalidMessageException e) {
            logger.warn("Failed to load sender key, resetting sender key: {}", e.getMessage());
            return null;
        }
    }

    private void storeSenderKeyLocked(final Key key, final SenderKeyRecord senderKeyRecord) {
        cachedSenderKeys.put(key, senderKeyRecord);

        final var file = getSenderKeyFile(key);
        try {
            try (var outputStream = new FileOutputStream(file)) {
                outputStream.write(senderKeyRecord.serialize());
            }
        } catch (IOException e) {
            logger.warn("Failed to store sender key, trying to delete file and retry: {}", e.getMessage());
            try {
                Files.delete(file.toPath());
                try (var outputStream = new FileOutputStream(file)) {
                    outputStream.write(senderKeyRecord.serialize());
                }
            } catch (IOException e2) {
                logger.error("Failed to store sender key file {}: {}", file, e2.getMessage());
            }
        }
    }

    private void deleteSenderKeyLocked(final Key key) {
        cachedSenderKeys.remove(key);

        final var file = getSenderKeyFile(key);
        if (!file.exists()) {
            return;
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete sender key file {}: {}", file, e.getMessage());
        }
    }

    private record Key(RecipientId recipientId, int deviceId, UUID distributionId) {}
}
