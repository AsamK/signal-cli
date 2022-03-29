package org.asamk.signal.manager.storage.identities;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Objects;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class IdentityKeyStore implements org.signal.libsignal.protocol.state.IdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(IdentityKeyStore.class);
    private final ObjectMapper objectMapper = org.asamk.signal.manager.storage.Utils.createStorageObjectMapper();

    private final Map<RecipientId, IdentityInfo> cachedIdentities = new HashMap<>();

    private final File identitiesPath;

    private final RecipientResolver resolver;
    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;
    private final TrustNewIdentity trustNewIdentity;
    private final PublishSubject<RecipientId> identityChanges = PublishSubject.create();

    private boolean isRetryingDecryption = false;

    public IdentityKeyStore(
            final File identitiesPath,
            final RecipientResolver resolver,
            final IdentityKeyPair identityKeyPair,
            final int localRegistrationId,
            final TrustNewIdentity trustNewIdentity
    ) {
        this.identitiesPath = identitiesPath;
        this.resolver = resolver;
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
        this.trustNewIdentity = trustNewIdentity;
    }

    public Subject<RecipientId> getIdentityChanges() {
        return identityChanges;
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
        if (isRetryingDecryption) {
            return false;
        }
        synchronized (cachedIdentities) {
            final var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo != null && identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity already exists, not updating the trust level
                logger.trace("Not storing new identity for recipient {}, identity already stored", recipientId);
                return false;
            }

            final var trustLevel = trustNewIdentity == TrustNewIdentity.ALWAYS || (
                    trustNewIdentity == TrustNewIdentity.ON_FIRST_USE && identityInfo == null
            ) ? TrustLevel.TRUSTED_UNVERIFIED : TrustLevel.UNTRUSTED;
            logger.debug("Storing new identity for recipient {} with trust {}", recipientId, trustLevel);
            final var newIdentityInfo = new IdentityInfo(recipientId, identityKey, trustLevel, added);
            storeIdentityLocked(recipientId, newIdentityInfo);
            identityChanges.onNext(recipientId);
            return true;
        }
    }

    public void setRetryingDecryption(final boolean retryingDecryption) {
        isRetryingDecryption = retryingDecryption;
    }

    public boolean setIdentityTrustLevel(RecipientId recipientId, IdentityKey identityKey, TrustLevel trustLevel) {
        synchronized (cachedIdentities) {
            final var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo == null) {
                logger.debug("Not updating trust level for recipient {}, identity not found", recipientId);
                return false;
            }
            if (!identityInfo.getIdentityKey().equals(identityKey)) {
                logger.debug("Not updating trust level for recipient {}, different identity found", recipientId);
                return false;
            }
            if (identityInfo.getTrustLevel() == trustLevel) {
                logger.trace("Not updating trust level for recipient {}, trust level already matches", recipientId);
                return false;
            }

            logger.debug("Updating trust level for recipient {} with trust {}", recipientId, trustLevel);
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
        if (trustNewIdentity == TrustNewIdentity.ALWAYS) {
            return true;
        }

        var recipientId = resolveRecipient(address.getName());

        synchronized (cachedIdentities) {
            // TODO implement possibility for different handling of incoming/outgoing trust decisions
            var identityInfo = loadIdentityLocked(recipientId);
            if (identityInfo == null) {
                logger.debug("Initial identity found for {}, saving.", recipientId);
                saveIdentity(address, identityKey);
                identityInfo = loadIdentityLocked(recipientId);
            } else if (!identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity found, but different
                if (direction == Direction.SENDING) {
                    logger.debug("Changed identity found for {}, saving.", recipientId);
                    saveIdentity(address, identityKey);
                    identityInfo = loadIdentityLocked(recipientId);
                } else {
                    logger.trace("Trusting identity for {} for {}: {}", recipientId, direction, false);
                    return false;
                }
            }

            final var isTrusted = identityInfo != null && identityInfo.isTrusted();
            logger.trace("Trusting identity for {} for {}: {}", recipientId, direction, isTrusted);
            return isTrusted;
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
                .map(f -> resolver.resolveRecipient(Long.parseLong(f.getName())))
                .filter(Objects::nonNull)
                .map(this::loadIdentityLocked)
                .toList();
    }

    public void mergeRecipients(final RecipientId recipientId, final RecipientId toBeMergedRecipientId) {
        synchronized (cachedIdentities) {
            deleteIdentityLocked(toBeMergedRecipientId);
        }
    }

    public void deleteIdentity(final RecipientId recipientId) {
        synchronized (cachedIdentities) {
            deleteIdentityLocked(recipientId);
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }

    private File getIdentityFile(final RecipientId recipientId) {
        try {
            IOUtils.createPrivateDirectories(identitiesPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create identities path", e);
        }
        return new File(identitiesPath, String.valueOf(recipientId.id()));
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

            var id = new IdentityKey(Base64.getDecoder().decode(storage.identityKey()));
            var trustLevel = TrustLevel.fromInt(storage.trustLevel());
            var added = new Date(storage.addedTimestamp());

            final var identityInfo = new IdentityInfo(recipientId, id, trustLevel, added);
            cachedIdentities.put(recipientId, identityInfo);
            return identityInfo;
        } catch (IOException | InvalidKeyException e) {
            logger.warn("Failed to load identity key: {}", e.getMessage());
            return null;
        }
    }

    private void storeIdentityLocked(final RecipientId recipientId, final IdentityInfo identityInfo) {
        logger.trace("Storing identity info for {}, trust: {}, added: {}",
                recipientId,
                identityInfo.getTrustLevel(),
                identityInfo.getDateAdded());
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

    private record IdentityStorage(String identityKey, int trustLevel, long addedTimestamp) {}
}
