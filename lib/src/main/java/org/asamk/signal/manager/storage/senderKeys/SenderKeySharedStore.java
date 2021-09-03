package org.asamk.signal.manager.storage.senderKeys;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SenderKeySharedStore {

    private final static Logger logger = LoggerFactory.getLogger(SenderKeySharedStore.class);

    private final Map<DistributionId, Set<SenderKeySharedEntry>> sharedSenderKeys;

    private final ObjectMapper objectMapper;
    private final File file;

    private final RecipientResolver resolver;
    private final RecipientAddressResolver addressResolver;

    public static SenderKeySharedStore load(
            final File file, final RecipientAddressResolver addressResolver, final RecipientResolver resolver
    ) throws IOException {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            final var storage = objectMapper.readValue(inputStream, Storage.class);
            final var sharedSenderKeys = new HashMap<DistributionId, Set<SenderKeySharedEntry>>();
            for (final var senderKey : storage.sharedSenderKeys) {
                final var entry = new SenderKeySharedEntry(RecipientId.of(senderKey.recipientId), senderKey.deviceId);
                final var uuid = UuidUtil.parseOrNull(senderKey.distributionId);
                if (uuid == null) {
                    logger.warn("Read invalid distribution id from storage {}, ignoring", senderKey.distributionId);
                    continue;
                }
                final var distributionId = DistributionId.from(uuid);
                var entries = sharedSenderKeys.get(distributionId);
                if (entries == null) {
                    entries = new HashSet<>();
                }
                entries.add(entry);
                sharedSenderKeys.put(distributionId, entries);
            }

            return new SenderKeySharedStore(sharedSenderKeys, objectMapper, file, addressResolver, resolver);
        } catch (FileNotFoundException e) {
            logger.debug("Creating new shared sender key store.");
            return new SenderKeySharedStore(new HashMap<>(), objectMapper, file, addressResolver, resolver);
        }
    }

    private SenderKeySharedStore(
            final Map<DistributionId, Set<SenderKeySharedEntry>> sharedSenderKeys,
            final ObjectMapper objectMapper,
            final File file,
            final RecipientAddressResolver addressResolver,
            final RecipientResolver resolver
    ) {
        this.sharedSenderKeys = sharedSenderKeys;
        this.objectMapper = objectMapper;
        this.file = file;
        this.addressResolver = addressResolver;
        this.resolver = resolver;
    }

    public Set<SignalProtocolAddress> getSenderKeySharedWith(final DistributionId distributionId) {
        synchronized (sharedSenderKeys) {
            return sharedSenderKeys.get(distributionId)
                    .stream()
                    .map(k -> new SignalProtocolAddress(addressResolver.resolveRecipientAddress(k.getRecipientId())
                            .getIdentifier(), k.getDeviceId()))
                    .collect(Collectors.toSet());
        }
    }

    public void markSenderKeySharedWith(
            final DistributionId distributionId, final Collection<SignalProtocolAddress> addresses
    ) {
        final var newEntries = addresses.stream()
                .map(a -> new SenderKeySharedEntry(resolveRecipient(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        synchronized (sharedSenderKeys) {
            final var previousEntries = sharedSenderKeys.getOrDefault(distributionId, Set.of());

            sharedSenderKeys.put(distributionId, new HashSet<>() {
                {
                    addAll(previousEntries);
                    addAll(newEntries);
                }
            });
            saveLocked();
        }
    }

    public void clearSenderKeySharedWith(final Collection<SignalProtocolAddress> addresses) {
        final var entriesToDelete = addresses.stream()
                .map(a -> new SenderKeySharedEntry(resolveRecipient(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        synchronized (sharedSenderKeys) {
            for (final var distributionId : sharedSenderKeys.keySet()) {
                final var entries = sharedSenderKeys.getOrDefault(distributionId, Set.of());

                sharedSenderKeys.put(distributionId, new HashSet<>(entries) {
                    {
                        removeAll(entriesToDelete);
                    }
                });
            }
            saveLocked();
        }
    }

    public void deleteAll() {
        synchronized (sharedSenderKeys) {
            sharedSenderKeys.clear();
            saveLocked();
        }
    }

    public void deleteAllFor(final RecipientId recipientId) {
        synchronized (sharedSenderKeys) {
            for (final var distributionId : sharedSenderKeys.keySet()) {
                final var entries = sharedSenderKeys.getOrDefault(distributionId, Set.of());

                sharedSenderKeys.put(distributionId, new HashSet<>(entries) {
                    {
                        entries.removeIf(e -> e.getRecipientId().equals(recipientId));
                    }
                });
            }
            saveLocked();
        }
    }

    public void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        synchronized (sharedSenderKeys) {
            for (final var distributionId : sharedSenderKeys.keySet()) {
                final var entries = sharedSenderKeys.getOrDefault(distributionId, Set.of());

                sharedSenderKeys.put(distributionId,
                        entries.stream()
                                .map(e -> e.recipientId.equals(toBeMergedRecipientId) ? new SenderKeySharedEntry(
                                        recipientId,
                                        e.getDeviceId()) : e)
                                .collect(Collectors.toSet()));
            }
            saveLocked();
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }

    private void saveLocked() {
        var storage = new Storage(sharedSenderKeys.entrySet().stream().flatMap(pair -> {
            final var sharedWith = pair.getValue();
            return sharedWith.stream()
                    .map(entry -> new Storage.SharedSenderKey(entry.getRecipientId().getId(),
                            entry.getDeviceId(),
                            pair.getKey().asUuid().toString()));
        }).collect(Collectors.toList()));

        // Write to memory first to prevent corrupting the file in case of serialization errors
        try (var inMemoryOutput = new ByteArrayOutputStream()) {
            objectMapper.writeValue(inMemoryOutput, storage);

            var input = new ByteArrayInputStream(inMemoryOutput.toByteArray());
            try (var outputStream = new FileOutputStream(file)) {
                input.transferTo(outputStream);
            }
        } catch (Exception e) {
            logger.error("Error saving shared sender key store file: {}", e.getMessage());
        }
    }

    private static class Storage {

        public List<SharedSenderKey> sharedSenderKeys;

        // For deserialization
        private Storage() {
        }

        public Storage(final List<SharedSenderKey> sharedSenderKeys) {
            this.sharedSenderKeys = sharedSenderKeys;
        }

        private static class SharedSenderKey {

            public long recipientId;
            public int deviceId;
            public String distributionId;

            // For deserialization
            private SharedSenderKey() {
            }

            public SharedSenderKey(final long recipientId, final int deviceId, final String distributionId) {
                this.recipientId = recipientId;
                this.deviceId = deviceId;
                this.distributionId = distributionId;
            }
        }
    }

    private static final class SenderKeySharedEntry {

        private final RecipientId recipientId;
        private final int deviceId;

        public SenderKeySharedEntry(
                final RecipientId recipientId, final int deviceId
        ) {
            this.recipientId = recipientId;
            this.deviceId = deviceId;
        }

        public RecipientId getRecipientId() {
            return recipientId;
        }

        public int getDeviceId() {
            return deviceId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final SenderKeySharedEntry that = (SenderKeySharedEntry) o;

            if (deviceId != that.deviceId) return false;
            return recipientId.equals(that.recipientId);
        }

        @Override
        public int hashCode() {
            int result = recipientId.hashCode();
            result = 31 * result + deviceId;
            return result;
        }
    }
}
