package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LegacyRecipientStore2 {

    private final static Logger logger = LoggerFactory.getLogger(LegacyRecipientStore2.class);

    public static void migrate(File file, RecipientStore recipientStore) {
        final var objectMapper = Utils.createStorageObjectMapper();
        try (var inputStream = new FileInputStream(file)) {
            final var storage = objectMapper.readValue(inputStream, Storage.class);

            final var recipients = storage.recipients.stream().map(r -> {
                final var recipientId = new RecipientId(r.id, recipientStore);
                final var address = new RecipientAddress(Optional.ofNullable(r.uuid).map(ServiceId::parseOrThrow),
                        Optional.ofNullable(r.number));

                Contact contact = null;
                if (r.contact != null) {
                    contact = new Contact(r.contact.name,
                            null,
                            r.contact.color,
                            r.contact.messageExpirationTime,
                            r.contact.blocked,
                            r.contact.archived,
                            r.contact.profileSharingEnabled);
                }

                ProfileKey profileKey = null;
                if (r.profileKey != null) {
                    try {
                        profileKey = new ProfileKey(Base64.getDecoder().decode(r.profileKey));
                    } catch (InvalidInputException ignored) {
                    }
                }

                ExpiringProfileKeyCredential expiringProfileKeyCredential = null;
                if (r.expiringProfileKeyCredential != null) {
                    try {
                        expiringProfileKeyCredential = new ExpiringProfileKeyCredential(Base64.getDecoder()
                                .decode(r.expiringProfileKeyCredential));
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
                            r.profile.avatarUrlPath,
                            r.profile.mobileCoinAddress == null
                                    ? null
                                    : Base64.getDecoder().decode(r.profile.mobileCoinAddress),
                            Profile.UnidentifiedAccessMode.valueOfOrUnknown(r.profile.unidentifiedAccessMode),
                            r.profile.capabilities.stream()
                                    .map(Profile.Capability::valueOfOrNull)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet()));
                }

                return new Recipient(recipientId, address, contact, profileKey, expiringProfileKeyCredential, profile);
            }).collect(Collectors.toMap(Recipient::getRecipientId, r -> r));

            recipientStore.addLegacyRecipients(recipients);
        } catch (FileNotFoundException e) {
            // nothing to migrate
        } catch (IOException e) {
            logger.warn("Failed to load recipient store", e);
            throw new RuntimeException(e);
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.warn("Failed to load recipient store", e);
            throw new RuntimeException(e);
        }
    }

    public record Storage(List<Recipient> recipients, long lastId) {

        public record Recipient(
                long id,
                String number,
                String uuid,
                String profileKey,
                String expiringProfileKeyCredential,
                Contact contact,
                Profile profile
        ) {

            public record Contact(
                    String name,
                    String color,
                    int messageExpirationTime,
                    boolean blocked,
                    boolean archived,
                    boolean profileSharingEnabled
            ) {}

            public record Profile(
                    long lastUpdateTimestamp,
                    String givenName,
                    String familyName,
                    String about,
                    String aboutEmoji,
                    String avatarUrlPath,
                    String mobileCoinAddress,
                    String unidentifiedAccessMode,
                    Set<String> capabilities
            ) {}
        }
    }
}
