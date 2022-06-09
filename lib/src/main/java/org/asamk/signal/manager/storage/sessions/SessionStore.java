package org.asamk.signal.manager.storage.sessions;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SessionStore implements SignalServiceSessionStore {

    private static final String TABLE_SESSION = "session";
    private final static Logger logger = LoggerFactory.getLogger(SessionStore.class);

    private final Map<Key, SessionRecord> cachedSessions = new HashMap<>();

    private final Database database;
    private final int accountIdType;
    private final RecipientResolver resolver;
    private final RecipientIdCreator recipientIdCreator;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE session (
                                      _id INTEGER PRIMARY KEY,
                                      account_id_type INTEGER NOT NULL,
                                      recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                      device_id INTEGER NOT NULL,
                                      record BLOB NOT NULL,
                                      UNIQUE(account_id_type, recipient_id, device_id)
                                    );
                                    """);
        }
    }

    public SessionStore(
            final Database database,
            final ServiceIdType serviceIdType,
            final RecipientResolver resolver,
            final RecipientIdCreator recipientIdCreator
    ) {
        this.database = database;
        this.accountIdType = Utils.getAccountIdType(serviceIdType);
        this.resolver = resolver;
        this.recipientIdCreator = recipientIdCreator;
    }

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        final var key = getKey(address);
        try (final var connection = database.getConnection()) {
            final var session = loadSession(connection, key);
            return Objects.requireNonNullElseGet(session, SessionRecord::new);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    @Override
    public List<SessionRecord> loadExistingSessions(final List<SignalProtocolAddress> addresses) throws NoSessionException {
        final var keys = addresses.stream().map(this::getKey).toList();

        try (final var connection = database.getConnection()) {
            final var sessions = new ArrayList<SessionRecord>();
            for (final var key : keys) {
                final var sessionRecord = loadSession(connection, key);
                if (sessionRecord != null) {
                    sessions.add(sessionRecord);
                }
            }

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
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        final var recipientId = resolver.resolveRecipient(name);
        // get all sessions for recipient except primary device session
        final var sql = (
                """
                SELECT s.device_id
                FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id = ? AND s.device_id != 1
                """
        ).formatted(TABLE_SESSION);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setLong(2, recipientId.id());
                return Utils.executeQueryForStream(statement, res -> res.getInt("device_id")).toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    public boolean isCurrentRatchetKey(RecipientId recipientId, int deviceId, ECPublicKey ratchetKey) {
        final var key = new Key(recipientId, deviceId);

        try (final var connection = database.getConnection()) {
            final var session = loadSession(connection, key);
            if (session == null) {
                return false;
            }
            return session.currentRatchetKeyMatches(ratchetKey);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord session) {
        final var key = getKey(address);

        try (final var connection = database.getConnection()) {
            storeSession(connection, key, session);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        final var key = getKey(address);

        try (final var connection = database.getConnection()) {
            final var session = loadSession(connection, key);
            return isActive(session);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        final var key = getKey(address);

        try (final var connection = database.getConnection()) {
            deleteSession(connection, key);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    @Override
    public void deleteAllSessions(String name) {
        final var recipientId = resolver.resolveRecipient(name);
        deleteAllSessions(recipientId);
    }

    public void deleteAllSessions(RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            deleteAllSessions(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    @Override
    public void archiveSession(final SignalProtocolAddress address) {
        final var key = getKey(address);

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var session = loadSession(connection, key);
            if (session != null) {
                session.archiveCurrentState();
                storeSession(connection, key, session);
                connection.commit();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    @Override
    public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(final List<String> addressNames) {
        final var recipientIdToNameMap = addressNames.stream()
                .collect(Collectors.toMap(resolver::resolveRecipient, name -> name));
        final var recipientIdsCommaSeparated = recipientIdToNameMap.keySet()
                .stream()
                .map(recipientId -> String.valueOf(recipientId.id()))
                .collect(Collectors.joining(","));
        final var sql = (
                """
                SELECT s.recipient_id, s.device_id, s.record
                FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id IN (%s)
                """
        ).formatted(TABLE_SESSION, recipientIdsCommaSeparated);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                return Utils.executeQueryForStream(statement,
                                res -> new Pair<>(getKeyFromResultSet(res), getSessionRecordFromResultSet(res)))
                        .filter(pair -> isActive(pair.second()))
                        .map(Pair::first)
                        .map(key -> new SignalProtocolAddress(recipientIdToNameMap.get(key.recipientId),
                                key.deviceId()))
                        .collect(Collectors.toSet());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from session store", e);
        }
    }

    public void archiveAllSessions() {
        final var sql = (
                """
                SELECT s.recipient_id, s.device_id, s.record
                FROM %s AS s
                WHERE s.account_id_type = ?
                """
        ).formatted(TABLE_SESSION);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final List<Pair<Key, SessionRecord>> records;
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                records = Utils.executeQueryForStream(statement,
                        res -> new Pair<>(getKeyFromResultSet(res), getSessionRecordFromResultSet(res))).toList();
            }
            for (final var record : records) {
                record.second().archiveCurrentState();
                storeSession(connection, record.first(), record.second());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    public void archiveSessions(final RecipientId recipientId) {
        final var sql = (
                """
                SELECT s.recipient_id, s.device_id, s.record
                FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id = ?
                """
        ).formatted(TABLE_SESSION);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final List<Pair<Key, SessionRecord>> records;
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setLong(2, recipientId.id());
                records = Utils.executeQueryForStream(statement,
                        res -> new Pair<>(getKeyFromResultSet(res), getSessionRecordFromResultSet(res))).toList();
            }
            for (final var record : records) {
                record.second().archiveCurrentState();
                storeSession(connection, record.first(), record.second());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    public void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            synchronized (cachedSessions) {
                cachedSessions.clear();
            }

            final var sql = """
                            UPDATE OR IGNORE %s
                            SET recipient_id = ?
                            WHERE account_id_type = ? AND recipient_id = ?
                            """.formatted(TABLE_SESSION);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setInt(2, accountIdType);
                statement.setLong(3, toBeMergedRecipientId.id());
                final var rows = statement.executeUpdate();
                if (rows > 0) {
                    logger.debug("Reassigned {} sessions of to be merged recipient.", rows);
                }
            }
            // Delete all conflicting sessions now
            deleteAllSessions(connection, toBeMergedRecipientId);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
    }

    void addLegacySessions(final Collection<Pair<Key, SessionRecord>> sessions) {
        logger.debug("Migrating legacy sessions to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var pair : sessions) {
                storeSession(connection, pair.first(), pair.second());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update session store", e);
        }
        logger.debug("Complete sessions migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private Key getKey(final SignalProtocolAddress address) {
        final var recipientId = resolver.resolveRecipient(address.getName());
        return new Key(recipientId, address.getDeviceId());
    }

    private SessionRecord loadSession(Connection connection, final Key key) throws SQLException {
        synchronized (cachedSessions) {
            final var session = cachedSessions.get(key);
            if (session != null) {
                return session;
            }
        }
        final var sql = (
                """
                SELECT s.record
                FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id = ? AND s.device_id = ?
                """
        ).formatted(TABLE_SESSION);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountIdType);
            statement.setLong(2, key.recipientId().id());
            statement.setInt(3, key.deviceId());
            return Utils.executeQueryForOptional(statement, this::getSessionRecordFromResultSet).orElse(null);
        }
    }

    private Key getKeyFromResultSet(ResultSet resultSet) throws SQLException {
        final var recipientId = resultSet.getLong("recipient_id");
        final var deviceId = resultSet.getInt("device_id");
        return new Key(recipientIdCreator.create(recipientId), deviceId);
    }

    private SessionRecord getSessionRecordFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var record = resultSet.getBytes("record");
            return new SessionRecord(record);
        } catch (InvalidMessageException e) {
            logger.warn("Failed to load session, resetting session: {}", e.getMessage());
            return null;
        }
    }

    private void storeSession(
            final Connection connection, final Key key, final SessionRecord session
    ) throws SQLException {
        synchronized (cachedSessions) {
            cachedSessions.put(key, session);
        }

        final var sql = """
                        INSERT OR REPLACE INTO %s (account_id_type, recipient_id, device_id, record)
                        VALUES (?, ?, ?, ?)
                        """.formatted(TABLE_SESSION);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountIdType);
            statement.setLong(2, key.recipientId().id());
            statement.setInt(3, key.deviceId());
            statement.setBytes(4, session.serialize());
            statement.executeUpdate();
        }
    }

    private void deleteAllSessions(final Connection connection, final RecipientId recipientId) throws SQLException {
        synchronized (cachedSessions) {
            cachedSessions.clear();
        }

        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id = ?
                """
        ).formatted(TABLE_SESSION);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountIdType);
            statement.setLong(2, recipientId.id());
            statement.executeUpdate();
        }
    }

    private void deleteSession(Connection connection, final Key key) throws SQLException {
        synchronized (cachedSessions) {
            cachedSessions.remove(key);
        }

        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.account_id_type = ? AND s.recipient_id = ? AND s.device_id = ?
                """
        ).formatted(TABLE_SESSION);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountIdType);
            statement.setLong(2, key.recipientId().id());
            statement.setInt(3, key.deviceId());
            statement.executeUpdate();
        }
    }

    private static boolean isActive(SessionRecord record) {
        return record != null
                && record.hasSenderChain()
                && record.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    }

    record Key(RecipientId recipientId, int deviceId) {}
}
