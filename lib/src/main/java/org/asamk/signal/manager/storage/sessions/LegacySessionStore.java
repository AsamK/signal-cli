package org.asamk.signal.manager.storage.sessions;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacySessionStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacySessionStore.class);

    public static void migrate(
            final File sessionsPath,
            final RecipientResolver resolver,
            final RecipientAddressResolver addressResolver,
            final SessionStore sessionStore
    ) {
        final var keys = getKeysLocked(sessionsPath, resolver);
        final var sessions = keys.stream().map(key -> {
            final var record = loadSessionLocked(key, sessionsPath);
            final var serviceId = addressResolver.resolveRecipientAddress(key.recipientId).serviceId();
            if (record == null || serviceId.isEmpty()) {
                return null;
            }
            return new Pair<>(new SessionStore.Key(serviceId.get(), key.deviceId()), record);
        }).filter(Objects::nonNull).toList();
        sessionStore.addLegacySessions(sessions);
        deleteAllSessions(sessionsPath);
    }

    private static void deleteAllSessions(File sessionsPath) {
        final var files = sessionsPath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete session file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(sessionsPath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete session directory {}: {}", sessionsPath, e.getMessage());
        }
    }

    private static Collection<Key> getKeysLocked(File sessionsPath, final RecipientResolver resolver) {
        final var files = sessionsPath.listFiles();
        if (files == null) {
            return List.of();
        }
        return parseFileNames(files, resolver);
    }

    static final Pattern sessionFileNamePattern = Pattern.compile("(\\d+)_(\\d+)");

    private static List<Key> parseFileNames(final File[] files, final RecipientResolver resolver) {
        return Arrays.stream(files)
                .map(f -> sessionFileNamePattern.matcher(f.getName()))
                .filter(Matcher::matches)
                .map(matcher -> {
                    final var recipientId = resolver.resolveRecipient(Long.parseLong(matcher.group(1)));
                    if (recipientId == null) {
                        return null;
                    }
                    return new Key(recipientId, Integer.parseInt(matcher.group(2)));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static File getSessionFile(Key key, final File sessionsPath) {
        try {
            IOUtils.createPrivateDirectories(sessionsPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create sessions path", e);
        }
        return new File(sessionsPath, key.recipientId().id() + "_" + key.deviceId());
    }

    private static SessionRecord loadSessionLocked(final Key key, final File sessionsPath) {
        final var file = getSessionFile(key, sessionsPath);
        if (!file.exists()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            return new SessionRecord(inputStream.readAllBytes());
        } catch (Exception e) {
            logger.warn("Failed to load session, resetting session: {}", e.getMessage());
            return null;
        }
    }

    record Key(RecipientId recipientId, int deviceId) {}
}
