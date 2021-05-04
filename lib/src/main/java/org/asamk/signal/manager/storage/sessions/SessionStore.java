package org.asamk.signal.manager.storage.sessions;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SessionStore implements SignalServiceSessionStore {

    private final static Logger logger = LoggerFactory.getLogger(SessionStore.class);

    private final Map<Key, SessionRecord> cachedSessions = new HashMap<>();

    private final File sessionsPath;

    private final RecipientResolver resolver;

    public SessionStore(
            final File sessionsPath, final RecipientResolver resolver
    ) {
        this.sessionsPath = sessionsPath;
        this.resolver = resolver;
    }

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        final var key = getKey(address);

        synchronized (cachedSessions) {
            final var session = loadSessionLocked(key);
            if (session == null) {
                return new SessionRecord();
            }
            return session;
        }
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        final var recipientId = resolveRecipient(name);

        synchronized (cachedSessions) {
            return getKeysLocked(recipientId).stream()
                    // get all sessions for recipient except main device session
                    .filter(key -> key.getDeviceId() != 1 && key.getRecipientId().equals(recipientId))
                    .map(Key::getDeviceId)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord session) {
        final var key = getKey(address);

        synchronized (cachedSessions) {
            storeSessionLocked(key, session);
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        final var key = getKey(address);

        synchronized (cachedSessions) {
            final var session = loadSessionLocked(key);
            if (session == null) {
                return false;
            }

            return session.hasSenderChain() && session.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
        }
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        final var key = getKey(address);

        synchronized (cachedSessions) {
            deleteSessionLocked(key);
        }
    }

    @Override
    public void deleteAllSessions(String name) {
        final var recipientId = resolveRecipient(name);
        deleteAllSessions(recipientId);
    }

    public void deleteAllSessions(RecipientId recipientId) {
        synchronized (cachedSessions) {
            final var keys = getKeysLocked(recipientId);
            for (var key : keys) {
                deleteSessionLocked(key);
            }
        }
    }

    @Override
    public void archiveSession(final SignalProtocolAddress address) {
        final var key = getKey(address);

        synchronized (cachedSessions) {
            archiveSessionLocked(key);
        }
    }

    public void archiveAllSessions() {
        synchronized (cachedSessions) {
            final var keys = getKeysLocked();
            for (var key : keys) {
                archiveSessionLocked(key);
            }
        }
    }

    public void archiveSessions(final RecipientId recipientId) {
        synchronized (cachedSessions) {
            getKeysLocked().stream()
                    .filter(key -> key.recipientId.equals(recipientId))
                    .forEach(this::archiveSessionLocked);
        }
    }

    public void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        synchronized (cachedSessions) {
            final var otherHasSession = getKeysLocked(toBeMergedRecipientId).size() > 0;
            if (!otherHasSession) {
                return;
            }

            final var hasSession = getKeysLocked(recipientId).size() > 0;
            if (hasSession) {
                logger.debug("To be merged recipient had sessions, deleting.");
                deleteAllSessions(toBeMergedRecipientId);
            } else {
                logger.debug("To be merged recipient had sessions, re-assigning to the new recipient.");
                final var keys = getKeysLocked(toBeMergedRecipientId);
                for (var key : keys) {
                    final var session = loadSessionLocked(key);
                    deleteSessionLocked(key);
                    if (session == null) {
                        continue;
                    }
                    final var newKey = new Key(recipientId, key.getDeviceId());
                    storeSessionLocked(newKey, session);
                }
            }
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(Utils.getSignalServiceAddressFromIdentifier(identifier));
    }

    private Key getKey(final SignalProtocolAddress address) {
        final var recipientId = resolveRecipient(address.getName());
        return new Key(recipientId, address.getDeviceId());
    }

    private List<Key> getKeysLocked(RecipientId recipientId) {
        final var files = sessionsPath.listFiles((_file, s) -> s.startsWith(recipientId.getId() + "_"));
        if (files == null) {
            return List.of();
        }
        return parseFileNames(files);
    }

    private Collection<Key> getKeysLocked() {
        final var files = sessionsPath.listFiles();
        if (files == null) {
            return List.of();
        }
        return parseFileNames(files);
    }

    final Pattern sessionFileNamePattern = Pattern.compile("([0-9]+)_([0-9]+)");

    private List<Key> parseFileNames(final File[] files) {
        return Arrays.stream(files)
                .map(f -> sessionFileNamePattern.matcher(f.getName()))
                .filter(Matcher::matches)
                .map(matcher -> new Key(RecipientId.of(Long.parseLong(matcher.group(1))),
                        Integer.parseInt(matcher.group(2))))
                .collect(Collectors.toList());
    }

    private File getSessionFile(Key key) {
        try {
            IOUtils.createPrivateDirectories(sessionsPath);
        } catch (IOException e) {
            throw new AssertionError("Failed to create sessions path", e);
        }
        return new File(sessionsPath, key.getRecipientId().getId() + "_" + key.getDeviceId());
    }

    private SessionRecord loadSessionLocked(final Key key) {
        {
            final var session = cachedSessions.get(key);
            if (session != null) {
                return session;
            }
        }

        final var file = getSessionFile(key);
        if (!file.exists()) {
            return null;
        }
        try (var inputStream = new FileInputStream(file)) {
            final var session = new SessionRecord(inputStream.readAllBytes());
            cachedSessions.put(key, session);
            return session;
        } catch (IOException e) {
            logger.warn("Failed to load session, resetting session: {}", e.getMessage());
            return null;
        }
    }

    private void storeSessionLocked(final Key key, final SessionRecord session) {
        cachedSessions.put(key, session);

        final var file = getSessionFile(key);
        try {
            try (var outputStream = new FileOutputStream(file)) {
                outputStream.write(session.serialize());
            }
        } catch (IOException e) {
            logger.warn("Failed to store session, trying to delete file and retry: {}", e.getMessage());
            try {
                Files.delete(file.toPath());
                try (var outputStream = new FileOutputStream(file)) {
                    outputStream.write(session.serialize());
                }
            } catch (IOException e2) {
                logger.error("Failed to store session file {}: {}", file, e2.getMessage());
            }
        }
    }

    private void archiveSessionLocked(final Key key) {
        final var session = loadSessionLocked(key);
        if (session == null) {
            return;
        }
        session.archiveCurrentState();
        storeSessionLocked(key, session);
    }

    private void deleteSessionLocked(final Key key) {
        cachedSessions.remove(key);

        final var file = getSessionFile(key);
        if (!file.exists()) {
            return;
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete session file {}: {}", file, e.getMessage());
        }
    }

    private static final class Key {

        private final RecipientId recipientId;
        private final int deviceId;

        public Key(final RecipientId recipientId, final int deviceId) {
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

            final var key = (Key) o;

            if (deviceId != key.deviceId) return false;
            return recipientId.equals(key.recipientId);
        }

        @Override
        public int hashCode() {
            int result = recipientId.hashCode();
            result = 31 * result + deviceId;
            return result;
        }
    }
}
