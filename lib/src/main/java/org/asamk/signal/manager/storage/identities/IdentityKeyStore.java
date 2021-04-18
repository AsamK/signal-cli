package org.asamk.signal.manager.storage.identities;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IdentityKeyStore implements org.whispersystems.libsignal.state.IdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(IdentityKeyStore.class);
    private final ObjectMapper objectMapper = org.asamk.signal.manager.storage.Utils.createStorageObjectMapper();

    private final Map<RecipientId, IdentityInfo> cachedIdentities = new HashMap<>();

    private final File identitiesPath;

    private final RecipientResolver resolver;
    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;

    public IdentityKeyStore(
            final File identitiesPath,
            final RecipientResolver resolver,
            final IdentityKeyPair identityKeyPair,
            final int localRegistrationId
    ) {
        this.identitiesPath = identitiesPath;
        this.resolver = resolver;
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        final var recipientId = resolveRecipient(address.getName());

        return saveIdentity(recipientId, identityKey, new Date());
    }

    public boolean saveIdentity(final RecipientId recipientId, final IdentityKey identityKey, Date added) {
        synchronized (cachedIdentities) {
            final var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo != null && identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity already exists, not updating the trust level
                return false;
            }

            final var trustLevel = identityInfo == null ? TrustLevel.TRUSTED_UNVERIFIED : TrustLevel.UNTRUSTED;
            final var newIdentityInfo = new IdentityInfo(recipientId, identityKey, trustLevel, added);
            storeIdentityLocked(recipientId, newIdentityInfo);
            return true;
        }
    }

    public boolean setIdentityTrustLevel(
            RecipientId recipientId, IdentityKey identityKey, TrustLevel trustLevel
    ) {
        synchronized (cachedIdentities) {
            final var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo == null || !identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity not found, not updating the trust level
                return false;
            }

            final var newIdentityInfo = new IdentityInfo(recipientId,
                    identityKey,
                    trustLevel,
                    identityInfo.getDateAdded());
            storeIdentityLocked(recipientId, newIdentityInfo);
            return true;
        }
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        var recipientId = resolveRecipient(address.getName());

        synchronized (cachedIdentities) {
            final var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo == null) {
                // Identity not found
                return true;
            }

            // TODO implement possibility for different handling of incoming/outgoing trust decisions
            if (!identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity found, but different
                return false;
            }

            return identityInfo.isTrusted();
        }
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        var recipientId = resolveRecipient(address.getName());

        synchronized (cachedIdentities) {
            var identity = loadIdentityLocked(recipientId);
            return identity == null ? null : identity.getIdentityKey();
        }
    }

    public IdentityInfo getIdentity(RecipientId recipientId) {
        synchronized (cachedIdentities) {
            return loadIdentityLocked(recipientId);
        }
    }

    final Pattern identityFileNamePattern = Pattern.compile("([0-9]+)");

    public List<IdentityInfo> getIdentities() {
        final var files = identitiesPath.listFiles();
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(f -> identityFileNamePattern.matcher(f.getName()).matches())
                .map(f -> RecipientId.of(Integer.parseInt(f.getName())))
                .map(this::loadIdentityLocked)
                .collect(Collectors.toList());
    }

    public void mergeRecipients(final RecipientId recipientId, final RecipientId toBeMergedRecipientId) {
        synchronized (cachedIdentities) {
            deleteIdentityLocked(toBeMergedRecipientId);
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(Utils.getSignalServiceAddressFromIdentifier(identifier));
    }

    private File getIdentityFile(final RecipientId recipientId) {
        try {
            IOUtils.createPrivateDirectories(identitiesPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create identities path", e);
        }
        return new File(identitiesPath, String.valueOf(recipientId.getId()));
    }

    private IdentityInfo loadIdentityLocked(final RecipientId recipientId) {
        {
            final var session = cachedIdentities.get(recipientId);
            if (session != null) {
                return session;
            }
        }

        final var file = getIdentityFile(recipientId);
        if (!file.exists()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            var storage = objectMapper.readValue(inputStream, IdentityStorage.class);

            var id = new IdentityKey(Base64.getDecoder().decode(storage.getIdentityKey()));
            var trustLevel = TrustLevel.fromInt(storage.getTrustLevel());
            var added = new Date(storage.getAddedTimestamp());

            final var identityInfo = new IdentityInfo(recipientId, id, trustLevel, added);
            cachedIdentities.put(recipientId, identityInfo);
            return identityInfo;
        } catch (IOException | InvalidKeyException e) {
            logger.warn("Failed to load identity key: {}", e.getMessage());
            return null;
        }
    }

    private void storeIdentityLocked(final RecipientId recipientId, final IdentityInfo identityInfo) {
        cachedIdentities.put(recipientId, identityInfo);

        var storage = new IdentityStorage(Base64.getEncoder().encodeToString(identityInfo.getIdentityKey().serialize()),
                identityInfo.getTrustLevel().ordinal(),
                identityInfo.getDateAdded().getTime());

        final var file = getIdentityFile(recipientId);
        // Write to memory first to prevent corrupting the file in case of serialization errors
        try (var inMemoryOutput = new ByteArrayOutputStream()) {
            objectMapper.writeValue(inMemoryOutput, storage);

            var input = new ByteArrayInputStream(inMemoryOutput.toByteArray());
            try (var outputStream = new FileOutputStream(file)) {
                input.transferTo(outputStream);
            }
        } catch (Exception e) {
            logger.error("Error saving identity file: {}", e.getMessage());
        }
    }

    private void deleteIdentityLocked(final RecipientId recipientId) {
        cachedIdentities.remove(recipientId);

        final var file = getIdentityFile(recipientId);
        if (!file.exists()) {
            return;
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete identity file {}: {}", file, e.getMessage());
        }
    }

    private static final class IdentityStorage {

        private String identityKey;
        private int trustLevel;
        private long addedTimestamp;

        // For deserialization
        private IdentityStorage() {
        }

        private IdentityStorage(final String identityKey, final int trustLevel, final long addedTimestamp) {
            this.identityKey = identityKey;
            this.trustLevel = trustLevel;
            this.addedTimestamp = addedTimestamp;
        }

        public String getIdentityKey() {
            return identityKey;
        }

        public int getTrustLevel() {
            return trustLevel;
        }

        public long getAddedTimestamp() {
            return addedTimestamp;
        }
    }
}
