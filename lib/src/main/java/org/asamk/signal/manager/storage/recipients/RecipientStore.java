package org.asamk.signal.manager.storage.recipients;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.storage.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecipientStore {

    private final static Logger logger = LoggerFactory.getLogger(RecipientStore.class);

    private final ObjectMapper objectMapper;
    private final File file;
    private final RecipientMergeHandler recipientMergeHandler;

    private final Map<RecipientId, SignalServiceAddress> recipients;
    private final Map<RecipientId, RecipientId> recipientsMerged = new HashMap<>();

    private long lastId;

    public static RecipientStore load(File file, RecipientMergeHandler recipientMergeHandler) throws IOException {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            var storage = objectMapper.readValue(inputStream, Storage.class);
            return new RecipientStore(objectMapper,
                    file,
                    recipientMergeHandler,
                    storage.recipients.stream()
                            .collect(Collectors.toMap(r -> new RecipientId(r.id),
                                    r -> new SignalServiceAddress(org.whispersystems.libsignal.util.guava.Optional.fromNullable(
                                            r.uuid).transform(UuidUtil::parseOrThrow),
                                            org.whispersystems.libsignal.util.guava.Optional.fromNullable(r.name)))),
                    storage.lastId);
        } catch (FileNotFoundException e) {
            logger.debug("Creating new recipient store.");
            return new RecipientStore(objectMapper, file, recipientMergeHandler, new HashMap<>(), 0);
        }
    }

    private RecipientStore(
            final ObjectMapper objectMapper,
            final File file,
            final RecipientMergeHandler recipientMergeHandler,
            final Map<RecipientId, SignalServiceAddress> recipients,
            final long lastId
    ) {
        this.objectMapper = objectMapper;
        this.file = file;
        this.recipientMergeHandler = recipientMergeHandler;
        this.recipients = recipients;
        this.lastId = lastId;
    }

    public SignalServiceAddress resolveServiceAddress(RecipientId recipientId) {
        synchronized (recipients) {
            while (recipientsMerged.containsKey(recipientId)) {
                recipientId = recipientsMerged.get(recipientId);
            }
            return recipients.get(recipientId);
        }
    }

    @Deprecated
    public SignalServiceAddress resolveServiceAddress(SignalServiceAddress address) {
        return resolveServiceAddress(resolveRecipient(address, true));
    }

    public RecipientId resolveRecipient(UUID uuid) {
        return resolveRecipient(new SignalServiceAddress(uuid, null), false);
    }

    public RecipientId resolveRecipient(String number) {
        return resolveRecipient(new SignalServiceAddress(null, number), false);
    }

    public RecipientId resolveRecipient(SignalServiceAddress address) {
        return resolveRecipient(address, true);
    }

    public List<RecipientId> resolveRecipients(List<SignalServiceAddress> addresses) {
        final List<RecipientId> recipientIds;
        final List<Pair<RecipientId, RecipientId>> toBeMerged = new ArrayList<>();
        synchronized (recipients) {
            recipientIds = addresses.stream().map(address -> {
                final var pair = resolveRecipientLocked(address, true);
                if (pair.second().isPresent()) {
                    toBeMerged.add(new Pair<>(pair.first(), pair.second().get()));
                }
                return pair.first();
            }).collect(Collectors.toList());
        }
        for (var pair : toBeMerged) {
            recipientMergeHandler.mergeRecipients(pair.first(), pair.second());
        }
        return recipientIds;
    }

    public RecipientId resolveRecipientUntrusted(SignalServiceAddress address) {
        return resolveRecipient(address, false);
    }

    /**
     * @param isHighTrust true, if the number/uuid connection was obtained from a trusted source.
     *                    Has no effect, if the address contains only a number or a uuid.
     */
    private RecipientId resolveRecipient(SignalServiceAddress address, boolean isHighTrust) {
        final Pair<RecipientId, Optional<RecipientId>> pair;
        synchronized (recipients) {
            pair = resolveRecipientLocked(address, isHighTrust);
            if (pair.second().isPresent()) {
                recipientsMerged.put(pair.second().get(), pair.first());
            }
        }

        if (pair.second().isPresent()) {
            recipientMergeHandler.mergeRecipients(pair.first(), pair.second().get());
        }
        return pair.first();
    }

    private Pair<RecipientId, Optional<RecipientId>> resolveRecipientLocked(
            SignalServiceAddress address, boolean isHighTrust
    ) {
        final var byNumber = !address.getNumber().isPresent()
                ? Optional.<RecipientId>empty()
                : findByName(address.getNumber().get());
        final var byUuid = !address.getUuid().isPresent()
                ? Optional.<RecipientId>empty()
                : findByUuid(address.getUuid().get());

        if (byNumber.isEmpty() && byUuid.isEmpty()) {
            logger.debug("Got new recipient, both uuid and number are unknown");

            if (isHighTrust || !address.getUuid().isPresent() || !address.getNumber().isPresent()) {
                return new Pair<>(addNewRecipient(address), Optional.empty());
            }

            return new Pair<>(addNewRecipient(new SignalServiceAddress(address.getUuid().get(), null)),
                    Optional.empty());
        }

        if (!isHighTrust
                || !address.getUuid().isPresent()
                || !address.getNumber().isPresent()
                || byNumber.equals(byUuid)) {
            return new Pair<>(byUuid.orElseGet(byNumber::get), Optional.empty());
        }

        if (byNumber.isEmpty()) {
            logger.debug("Got recipient existing with uuid, updating with high trust number");
            recipients.put(byUuid.get(), address);
            save();
            return new Pair<>(byUuid.get(), Optional.empty());
        }

        if (byUuid.isEmpty()) {
            logger.debug("Got recipient existing with number, updating with high trust uuid");
            recipients.put(byNumber.get(), address);
            save();
            return new Pair<>(byNumber.get(), Optional.empty());
        }

        final var byNumberAddress = recipients.get(byNumber.get());
        if (byNumberAddress.getUuid().isPresent()) {
            logger.debug(
                    "Got separate recipients for high trust number and uuid, recipient for number has different uuid, so stripping its number");

            recipients.put(byNumber.get(), new SignalServiceAddress(byNumberAddress.getUuid().get(), null));
            recipients.put(byUuid.get(), address);
            save();
            return new Pair<>(byUuid.get(), Optional.empty());
        }

        logger.debug("Got separate recipients for high trust number and uuid, need to merge them");
        recipients.put(byUuid.get(), address);
        recipients.remove(byNumber.get());
        save();
        return new Pair<>(byUuid.get(), byNumber);
    }

    private RecipientId addNewRecipient(final SignalServiceAddress serviceAddress) {
        final var nextRecipientId = nextId();
        recipients.put(nextRecipientId, serviceAddress);
        save();
        return nextRecipientId;
    }

    private Optional<RecipientId> findByName(final String number) {
        return recipients.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getNumber().isPresent() && number.equals(entry.getValue()
                        .getNumber()
                        .get()))
                .findFirst()
                .map(Map.Entry::getKey);
    }

    private Optional<RecipientId> findByUuid(final UUID uuid) {
        return recipients.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getUuid().isPresent() && uuid.equals(entry.getValue()
                        .getUuid()
                        .get()))
                .findFirst()
                .map(Map.Entry::getKey);
    }

    private RecipientId nextId() {
        return new RecipientId(++this.lastId);
    }

    private void save() {
        var storage = new Storage(recipients.entrySet()
                .stream()
                .map(pair -> new Storage.Recipient(pair.getKey().getId(),
                        pair.getValue().getNumber().orNull(),
                        pair.getValue().getUuid().transform(UUID::toString).orNull()))
                .collect(Collectors.toList()), lastId);

        // Write to memory first to prevent corrupting the file in case of serialization errors
        try (var inMemoryOutput = new ByteArrayOutputStream()) {
            objectMapper.writeValue(inMemoryOutput, storage);

            var input = new ByteArrayInputStream(inMemoryOutput.toByteArray());
            try (var outputStream = new FileOutputStream(file)) {
                input.transferTo(outputStream);
            }
        } catch (Exception e) {
            logger.error("Error saving recipient store file: {}", e.getMessage());
        }
    }

    public boolean isEmpty() {
        synchronized (recipients) {
            return recipients.isEmpty();
        }
    }

    private static class Storage {

        private List<Recipient> recipients;

        private long lastId;

        // For deserialization
        private Storage() {
        }

        public Storage(final List<Recipient> recipients, final long lastId) {
            this.recipients = recipients;
            this.lastId = lastId;
        }

        public List<Recipient> getRecipients() {
            return recipients;
        }

        public long getLastId() {
            return lastId;
        }

        public static class Recipient {

            private long id;
            private String name;
            private String uuid;

            // For deserialization
            private Recipient() {
            }

            public Recipient(final long id, final String name, final String uuid) {
                this.id = id;
                this.name = name;
                this.uuid = uuid;
            }

            public long getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getUuid() {
                return uuid;
            }
        }
    }

    public interface RecipientMergeHandler {

        void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId);
    }
}
