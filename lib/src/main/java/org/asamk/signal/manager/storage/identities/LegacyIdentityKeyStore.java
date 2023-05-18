package org.asamk.signal.manager.storage.identities;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class LegacyIdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacyIdentityKeyStore.class);
    private static final ObjectMapper objectMapper = org.asamk.signal.manager.storage.Utils.createStorageObjectMapper();

    public static void migrate(
            final File identitiesPath,
            final RecipientResolver resolver,
            final RecipientAddressResolver addressResolver,
            final IdentityKeyStore identityKeyStore
    ) {
        final var identities = getIdentities(identitiesPath, resolver, addressResolver);
        identityKeyStore.addLegacyIdentities(identities);
        removeIdentityFiles(identitiesPath);
    }

    static final Pattern identityFileNamePattern = Pattern.compile("(\\d+)");

    private static List<IdentityInfo> getIdentities(
            final File identitiesPath, final RecipientResolver resolver, final RecipientAddressResolver addressResolver
    ) {
        final var files = identitiesPath.listFiles();
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .filter(f -> identityFileNamePattern.matcher(f.getName()).matches())
                .map(f -> resolver.resolveRecipient(Long.parseLong(f.getName())))
                .filter(Objects::nonNull)
                .map(recipientId -> loadIdentityLocked(recipientId, addressResolver, identitiesPath))
                .filter(Objects::nonNull)
                .toList();
    }

    private static File getIdentityFile(final RecipientId recipientId, final File identitiesPath) {
        try {
            IOUtils.createPrivateDirectories(identitiesPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create identities path", e);
        }
        return new File(identitiesPath, String.valueOf(recipientId.id()));
    }

    private static IdentityInfo loadIdentityLocked(
            final RecipientId recipientId, RecipientAddressResolver addressResolver, final File identitiesPath
    ) {
        final var file = getIdentityFile(recipientId, identitiesPath);
        if (!file.exists()) {
            return null;
        }
        final var address = addressResolver.resolveRecipientAddress(recipientId);
        if (address.serviceId().isEmpty()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            var storage = objectMapper.readValue(inputStream, IdentityStorage.class);

            var id = new IdentityKey(Base64.getDecoder().decode(storage.identityKey()));
            var trustLevel = TrustLevel.fromInt(storage.trustLevel());
            var added = storage.addedTimestamp();

            final var serviceId = address.serviceId().get();
            return new IdentityInfo(serviceId, id, trustLevel, added);
        } catch (IOException | InvalidKeyException e) {
            logger.warn("Failed to load identity key: {}", e.getMessage());
            return null;
        }
    }

    private static void removeIdentityFiles(File identitiesPath) {
        final var files = identitiesPath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete identity file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(identitiesPath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete identity directory {}: {}", identitiesPath, e.getMessage());
        }
    }

    public record IdentityStorage(String identityKey, int trustLevel, long addedTimestamp) {}
}
