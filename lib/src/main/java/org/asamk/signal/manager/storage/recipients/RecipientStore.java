package org.asamk.signal.manager.storage.recipients;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.contacts.ContactsStore;
import org.asamk.signal.manager.storage.profiles.ProfileStore;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecipientStore implements ContactsStore, ProfileStore {

    private final static Logger logger = LoggerFactory.getLogger(RecipientStore.class);

    private final ObjectMapper objectMapper;
    private final File file;
    private final RecipientMergeHandler recipientMergeHandler;

    private final Map<RecipientId, Recipient> recipients;
    private final Map<RecipientId, RecipientId> recipientsMerged = new HashMap<>();

    private long lastId;

    public static RecipientStore load(File file, RecipientMergeHandler recipientMergeHandler) throws IOException {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            final var storage = objectMapper.readValue(inputStream, Storage.class);
            final var recipients = storage.recipients.stream().map(r -> {
                final var recipientId = new RecipientId(r.id);
                final var address = new SignalServiceAddress(org.whispersystems.libsignal.util.guava.Optional.fromNullable(
                        r.uuid).transform(UuidUtil::parseOrThrow),
                        org.whispersystems.libsignal.util.guava.Optional.fromNullable(r.number));

                Contact contact = null;
                if (r.contact != null) {
                    contact = new Contact(r.contact.name,
                            r.contact.color,
                            r.contact.messageExpirationTime,
                            r.contact.blocked,
                            r.contact.archived);
                }

                ProfileKey profileKey = null;
                if (r.profileKey != null) {
                    try {
                        profileKey = new ProfileKey(Base64.getDecoder().decode(r.profileKey));
                    } catch (InvalidInputException ignored) {
                    }
                }

                ProfileKeyCredential profileKeyCredential = null;
                if (r.profileKeyCredential != null) {
                    try {
                        profileKeyCredential = new ProfileKeyCredential(Base64.getDecoder()
                                .decode(r.profileKeyCredential));
                    } catch (Throwable ignored) {
                    }
                }

                Profile profile = null;
                if (r.profile != null) {
                    profile = new Profile(r.profile.lastUpdateTimestamp,
                            r.profile.givenName,
                            r.profile.familyName,
                            r.profile.about,
                            r.profile.aboutEmoji,
                            Profile.UnidentifiedAccessMode.valueOfOrUnknown(r.profile.unidentifiedAccessMode),
                            r.profile.capabilities.stream()
                                    .map(Profile.Capability::valueOfOrNull)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet()));
                }

                return new Recipient(recipientId, address, contact, profileKey, profileKeyCredential, profile);
            }).collect(Collectors.toMap(Recipient::getRecipientId, r -> r));

            return new RecipientStore(objectMapper, file, recipientMergeHandler, recipients, storage.lastId);
        } catch (FileNotFoundException e) {
            logger.debug("Creating new recipient store.");
            return new RecipientStore(objectMapper, file, recipientMergeHandler, new HashMap<>(), 0);
        }
    }

    private RecipientStore(
            final ObjectMapper objectMapper,
            final File file,
            final RecipientMergeHandler recipientMergeHandler,
            final Map<RecipientId, Recipient> recipients,
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
            return getRecipient(recipientId).getAddress();
        }
    }

    public Recipient getRecipient(RecipientId recipientId) {
        synchronized (recipients) {
            while (recipientsMerged.containsKey(recipientId)) {
                recipientId = recipientsMerged.get(recipientId);
            }
            return recipients.get(recipientId);
        }
    }

    @Deprecated
    public SignalServiceAddress resolveServiceAddress(SignalServiceAddress address) {
        return resolveServiceAddress(resolveRecipient(address, false));
    }

    public RecipientId resolveRecipient(UUID uuid) {
        return resolveRecipient(new SignalServiceAddress(uuid, null), false);
    }

    public RecipientId resolveRecipient(String number) {
        return resolveRecipient(new SignalServiceAddress(null, number), false);
    }

    public RecipientId resolveRecipientTrusted(SignalServiceAddress address) {
        return resolveRecipient(address, true);
    }

    public List<RecipientId> resolveRecipientsTrusted(List<SignalServiceAddress> addresses) {
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

    public RecipientId resolveRecipient(SignalServiceAddress address) {
        return resolveRecipient(address, false);
    }

    @Override
    public void storeContact(final RecipientId recipientId, final Contact contact) {
        synchronized (recipients) {
            final var recipient = recipients.get(recipientId);
            storeRecipientLocked(recipientId, Recipient.newBuilder(recipient).withContact(contact).build());
        }
    }

    @Override
    public Contact getContact(final RecipientId recipientId) {
        final var recipient = getRecipient(recipientId);
        return recipient == null ? null : recipient.getContact();
    }

    @Override
    public List<Pair<RecipientId, Contact>> getContacts() {
        return recipients.entrySet()
                .stream()
                .filter(e -> e.getValue().getContact() != null)
                .map(e -> new Pair<>(e.getKey(), e.getValue().getContact()))
                .collect(Collectors.toList());
    }

    @Override
    public Profile getProfile(final RecipientId recipientId) {
        final var recipient = getRecipient(recipientId);
        return recipient == null ? null : recipient.getProfile();
    }

    @Override
    public ProfileKey getProfileKey(final RecipientId recipientId) {
        final var recipient = getRecipient(recipientId);
        return recipient == null ? null : recipient.getProfileKey();
    }

    @Override
    public ProfileKeyCredential getProfileKeyCredential(final RecipientId recipientId) {
        final var recipient = getRecipient(recipientId);
        return recipient == null ? null : recipient.getProfileKeyCredential();
    }

    @Override
    public void storeProfile(final RecipientId recipientId, final Profile profile) {
        synchronized (recipients) {
            final var recipient = recipients.get(recipientId);
            storeRecipientLocked(recipientId, Recipient.newBuilder(recipient).withProfile(profile).build());
        }
    }

    @Override
    public void storeProfileKey(final RecipientId recipientId, final ProfileKey profileKey) {
        synchronized (recipients) {
            final var recipient = recipients.get(recipientId);
            if (profileKey != null && profileKey.equals(recipient.getProfileKey())) {
                return;
            }

            final var newRecipient = Recipient.newBuilder(recipient)
                    .withProfileKey(profileKey)
                    .withProfileKeyCredential(null)
                    .withProfile(recipient.getProfile() == null
                            ? null
                            : Profile.newBuilder(recipient.getProfile()).withLastUpdateTimestamp(0).build())
                    .build();
            storeRecipientLocked(recipientId, newRecipient);
        }
    }

    @Override
    public void storeProfileKeyCredential(
            final RecipientId recipientId, final ProfileKeyCredential profileKeyCredential
    ) {
        synchronized (recipients) {
            final var recipient = recipients.get(recipientId);
            storeRecipientLocked(recipientId,
                    Recipient.newBuilder(recipient).withProfileKeyCredential(profileKeyCredential).build());
        }
    }

    public boolean isEmpty() {
        synchronized (recipients) {
            return recipients.isEmpty();
        }
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
                ? Optional.<Recipient>empty()
                : findByNameLocked(address.getNumber().get());
        final var byUuid = !address.getUuid().isPresent()
                ? Optional.<Recipient>empty()
                : findByUuidLocked(address.getUuid().get());

        if (byNumber.isEmpty() && byUuid.isEmpty()) {
            logger.debug("Got new recipient, both uuid and number are unknown");

            if (isHighTrust || !address.getUuid().isPresent() || !address.getNumber().isPresent()) {
                return new Pair<>(addNewRecipientLocked(address), Optional.empty());
            }

            return new Pair<>(addNewRecipientLocked(new SignalServiceAddress(address.getUuid().get(), null)),
                    Optional.empty());
        }

        if (!isHighTrust
                || !address.getUuid().isPresent()
                || !address.getNumber().isPresent()
                || byNumber.equals(byUuid)) {
            return new Pair<>(byUuid.or(() -> byNumber).map(Recipient::getRecipientId).get(), Optional.empty());
        }

        if (byNumber.isEmpty()) {
            logger.debug("Got recipient existing with uuid, updating with high trust number");
            updateRecipientAddressLocked(byUuid.get().getRecipientId(), address);
            return new Pair<>(byUuid.get().getRecipientId(), Optional.empty());
        }

        if (byUuid.isEmpty()) {
            if (byNumber.get().getAddress().getUuid().isPresent()) {
                logger.debug(
                        "Got recipient existing with number, but different uuid, so stripping its number and adding new recipient");

                updateRecipientAddressLocked(byNumber.get().getRecipientId(),
                        new SignalServiceAddress(byNumber.get().getAddress().getUuid().get(), null));
                return new Pair<>(addNewRecipientLocked(address), Optional.empty());
            }

            logger.debug("Got recipient existing with number and no uuid, updating with high trust uuid");
            updateRecipientAddressLocked(byNumber.get().getRecipientId(), address);
            return new Pair<>(byNumber.get().getRecipientId(), Optional.empty());
        }

        if (byNumber.get().getAddress().getUuid().isPresent()) {
            logger.debug(
                    "Got separate recipients for high trust number and uuid, recipient for number has different uuid, so stripping its number");

            updateRecipientAddressLocked(byNumber.get().getRecipientId(),
                    new SignalServiceAddress(byNumber.get().getAddress().getUuid().get(), null));
            updateRecipientAddressLocked(byUuid.get().getRecipientId(), address);
            return new Pair<>(byUuid.get().getRecipientId(), Optional.empty());
        }

        logger.debug("Got separate recipients for high trust number and uuid, need to merge them");
        updateRecipientAddressLocked(byUuid.get().getRecipientId(), address);
        mergeRecipientsLocked(byUuid.get().getRecipientId(), byNumber.get().getRecipientId());
        return new Pair<>(byUuid.get().getRecipientId(), byNumber.map(Recipient::getRecipientId));
    }

    private RecipientId addNewRecipientLocked(final SignalServiceAddress serviceAddress) {
        final var nextRecipientId = nextIdLocked();
        storeRecipientLocked(nextRecipientId, new Recipient(nextRecipientId, serviceAddress, null, null, null, null));
        return nextRecipientId;
    }

    private void updateRecipientAddressLocked(
            final RecipientId recipientId, final SignalServiceAddress address
    ) {
        final var recipient = recipients.get(recipientId);
        storeRecipientLocked(recipientId, Recipient.newBuilder(recipient).withAddress(address).build());
    }

    private void storeRecipientLocked(
            final RecipientId recipientId, final Recipient recipient
    ) {
        recipients.put(recipientId, recipient);
        saveLocked();
    }

    private void mergeRecipientsLocked(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        final var recipient = recipients.get(recipientId);
        final var toBeMergedRecipient = recipients.get(toBeMergedRecipientId);
        recipients.put(recipientId,
                new Recipient(recipientId,
                        recipient.getAddress(),
                        recipient.getContact() != null ? recipient.getContact() : toBeMergedRecipient.getContact(),
                        recipient.getProfileKey() != null
                                ? recipient.getProfileKey()
                                : toBeMergedRecipient.getProfileKey(),
                        recipient.getProfileKeyCredential() != null
                                ? recipient.getProfileKeyCredential()
                                : toBeMergedRecipient.getProfileKeyCredential(),
                        recipient.getProfile() != null ? recipient.getProfile() : toBeMergedRecipient.getProfile()));
        recipients.remove(toBeMergedRecipientId);
        saveLocked();
    }

    private Optional<Recipient> findByNameLocked(final String number) {
        return recipients.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getAddress().getNumber().isPresent() && number.equals(entry.getValue()
                        .getAddress()
                        .getNumber()
                        .get()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    private Optional<Recipient> findByUuidLocked(final UUID uuid) {
        return recipients.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getAddress().getUuid().isPresent() && uuid.equals(entry.getValue()
                        .getAddress()
                        .getUuid()
                        .get()))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    private RecipientId nextIdLocked() {
        return new RecipientId(++this.lastId);
    }

    private void saveLocked() {
        final var base64 = Base64.getEncoder();
        var storage = new Storage(recipients.entrySet().stream().map(pair -> {
            final var recipient = pair.getValue();
            final var contact = recipient.getContact() == null
                    ? null
                    : new Storage.Recipient.Contact(recipient.getContact().getName(),
                            recipient.getContact().getColor(),
                            recipient.getContact().getMessageExpirationTime(),
                            recipient.getContact().isBlocked(),
                            recipient.getContact().isArchived());
            final var profile = recipient.getProfile() == null
                    ? null
                    : new Storage.Recipient.Profile(recipient.getProfile().getLastUpdateTimestamp(),
                            recipient.getProfile().getGivenName(),
                            recipient.getProfile().getFamilyName(),
                            recipient.getProfile().getAbout(),
                            recipient.getProfile().getAboutEmoji(),
                            recipient.getProfile().getUnidentifiedAccessMode().name(),
                            recipient.getProfile()
                                    .getCapabilities()
                                    .stream()
                                    .map(Enum::name)
                                    .collect(Collectors.toSet()));
            return new Storage.Recipient(pair.getKey().getId(),
                    recipient.getAddress().getNumber().orNull(),
                    recipient.getAddress().getUuid().transform(UUID::toString).orNull(),
                    recipient.getProfileKey() == null
                            ? null
                            : base64.encodeToString(recipient.getProfileKey().serialize()),
                    recipient.getProfileKeyCredential() == null
                            ? null
                            : base64.encodeToString(recipient.getProfileKeyCredential().serialize()),
                    contact,
                    profile);
        }).collect(Collectors.toList()), lastId);

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

    private static class Storage {

        public List<Recipient> recipients;

        public long lastId;

        // For deserialization
        private Storage() {
        }

        public Storage(final List<Recipient> recipients, final long lastId) {
            this.recipients = recipients;
            this.lastId = lastId;
        }

        private static class Recipient {

            public long id;
            public String number;
            public String uuid;
            public String profileKey;
            public String profileKeyCredential;
            public Contact contact;
            public Profile profile;

            // For deserialization
            private Recipient() {
            }

            public Recipient(
                    final long id,
                    final String number,
                    final String uuid,
                    final String profileKey,
                    final String profileKeyCredential,
                    final Contact contact,
                    final Profile profile
            ) {
                this.id = id;
                this.number = number;
                this.uuid = uuid;
                this.profileKey = profileKey;
                this.profileKeyCredential = profileKeyCredential;
                this.contact = contact;
                this.profile = profile;
            }

            private static class Contact {

                public String name;
                public String color;
                public int messageExpirationTime;
                public boolean blocked;
                public boolean archived;

                // For deserialization
                public Contact() {
                }

                public Contact(
                        final String name,
                        final String color,
                        final int messageExpirationTime,
                        final boolean blocked,
                        final boolean archived
                ) {
                    this.name = name;
                    this.color = color;
                    this.messageExpirationTime = messageExpirationTime;
                    this.blocked = blocked;
                    this.archived = archived;
                }
            }

            private static class Profile {

                public long lastUpdateTimestamp;
                public String givenName;
                public String familyName;
                public String about;
                public String aboutEmoji;
                public String unidentifiedAccessMode;
                public Set<String> capabilities;

                // For deserialization
                private Profile() {
                }

                public Profile(
                        final long lastUpdateTimestamp,
                        final String givenName,
                        final String familyName,
                        final String about,
                        final String aboutEmoji,
                        final String unidentifiedAccessMode,
                        final Set<String> capabilities
                ) {
                    this.lastUpdateTimestamp = lastUpdateTimestamp;
                    this.givenName = givenName;
                    this.familyName = familyName;
                    this.about = about;
                    this.aboutEmoji = aboutEmoji;
                    this.unidentifiedAccessMode = unidentifiedAccessMode;
                    this.capabilities = capabilities;
                }
            }
        }
    }

    public interface RecipientMergeHandler {

        void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId);
    }
}
