package org.asamk.signal.manager.storage.sessions;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.NoSessionException;
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
import java.util.Objects;
import java.util.Set;
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
    public List<SessionRecord> loadExistingSessions(final List<SignalProtocolAddress> addresses) throws NoSessionException {
        final var keys = addresses.stream().map(this::getKey).collect(Collectors.toList());

        synchronized (cachedSessions) {
            final var sessions = keys.stream()
                    .map(this::loadSessionLocked)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (sessions.size() != addresses.size()) {
                String message = "Mismatch! Asked for "
                        + addresses.size()
                        + " sessions, but only found "
                        + sessions.size()
                        + "!";
                logger.warn(message);
                throw new NoSessionException(message);
            }

            return sessions;
        }
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        final var recipientId = resolveRecipient(name);

        synchronized (cachedSessions) {
            return getKeysLocked(recipientId).stream()
                    // get all sessions for recipient except main device session
                    .filter(key -> key.deviceId() != 1 && key.recipientId().equals(recipientId))
                    .map(Key::deviceId)
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
            return isActive(session);
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

    @Override
    public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(final List<String> addressNames) {
        final var recipientIdToNameMap = addressNames.stream()
                .collect(Collectors.toMap(this::resolveRecipient, name -> name));
        synchronized (cachedSessions) {
            return recipientIdToNameMap.keySet()
                    .stream()
                    .flatMap(recipientId -> getKeysLocked(recipientId).stream())
                    .filter(key -> isActive(this.loadSessionLocked(key)))
                    .map(key -> new SignalProtocolAddress(recipientIdToNameMap.get(key.recipientId), key.deviceId()))
                    .collect(Collectors.toSet());
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
            final var keys = getKeysLocked(toBeMergedRecipientId);
            final var otherHasSession = keys.size() > 0;
            if (!otherHasSession) {
                return;
            }

            final var hasSession = getKeysLocked(recipientId).size() > 0;
            if (hasSession) {
                logger.debug("To be merged recipient had sessions, deleting.");
                deleteAllSessions(toBeMergedRecipientId);
            } else {
                logger.debug("Only to be merged recipient had sessions, re-assigning to the new recipient.");
                for (var key : keys) {
                    final var session = loadSessionLocked(key);
                    deleteSessionLocked(key);
                    if (session == null) {
                        continue;
                    }
                    final var newKey = new Key(recipientId, key.deviceId());
                    storeSessionLocked(newKey, session);
                }
            }
        }
    }

    /**
     * @param identifier can be either a serialized uuid or a e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }

    private Key getKey(final SignalProtocolAddress address) {
        final var recipientId = resolveRecipient(address.getName());
        return new Key(recipientId, address.getDeviceId());
    }

    private List<Key> getKeysLocked(RecipientId recipientId) {
        final var files = sessionsPath.listFiles((_file, s) -> s.startsWith(recipientId.id() + "_"));
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
        return new File(sessionsPath, key.recipientId().id() + "_" + key.deviceId());
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

    private static boolean isActive(SessionRecord record) {
        return record != null
                && record.hasSenderChain()
                && record.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    }

    private record Key(RecipientId recipientId, int deviceId) {}
}
