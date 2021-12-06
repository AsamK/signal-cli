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
                final var recipientId = resolver.resolveRecipient(senderKey.recipientId);
                if (recipientId == null) {
                    continue;
                }
                final var entry = new SenderKeySharedEntry(recipientId, senderKey.deviceId);
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
                    .map(k -> new SignalProtocolAddress(addressResolver.resolveRecipientAddress(k.recipientId())
                            .getIdentifier(), k.deviceId()))
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
                        removeIf(e -> e.recipientId().equals(recipientId));
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
                                        e.deviceId()) : e)
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
                    .map(entry -> new Storage.SharedSenderKey(entry.recipientId().id(),
                            entry.deviceId(),
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

    private record Storage(List<SharedSenderKey> sharedSenderKeys) {

        private record SharedSenderKey(long recipientId, int deviceId, String distributionId) {}
    }

    private record SenderKeySharedEntry(RecipientId recipientId, int deviceId) {}
}
