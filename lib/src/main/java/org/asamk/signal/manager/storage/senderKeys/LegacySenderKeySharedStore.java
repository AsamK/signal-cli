package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.storage.senderKeys.SenderKeySharedStore.SenderKeySharedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LegacySenderKeySharedStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacySenderKeySharedStore.class);

    public static void migrate(
            final File file,
            final RecipientResolver resolver,
            final RecipientAddressResolver addressResolver,
            final SenderKeyStore senderKeyStore
    ) {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            final var storage = objectMapper.readValue(inputStream, Storage.class);
            final var sharedSenderKeys = new HashMap<DistributionId, Set<SenderKeySharedEntry>>();
            for (final var senderKey : storage.sharedSenderKeys) {
                final var recipientId = resolver.resolveRecipient(senderKey.recipientId);
                if (recipientId == null) {
                    continue;
                }
                final var serviceId = addressResolver.resolveRecipientAddress(recipientId).serviceId();
                if (serviceId.isEmpty()) {
                    continue;
                }
                final var entry = new SenderKeySharedEntry(serviceId.get(), senderKey.deviceId);
                final var distributionId = DistributionId.from(senderKey.distributionId);
                var entries = sharedSenderKeys.get(distributionId);
                if (entries == null) {
                    entries = new HashSet<>();
                }
                entries.add(entry);
                sharedSenderKeys.put(distributionId, entries);
            }

            senderKeyStore.addLegacySenderKeysShared(sharedSenderKeys);
        } catch (IOException e) {
            logger.info("Failed to load shared sender key store, ignoring", e);
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.info("Failed to load shared sender key store, ignoring", e);
        }
    }

    private record Storage(List<SharedSenderKey> sharedSenderKeys) {

        private record SharedSenderKey(long recipientId, int deviceId, String distributionId) {}
    }
}
