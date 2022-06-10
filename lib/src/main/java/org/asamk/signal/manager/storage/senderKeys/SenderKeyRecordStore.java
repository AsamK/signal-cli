package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.groups.state.SenderKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

public class SenderKeyRecordStore implements SenderKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(SenderKeyRecordStore.class);
    private final static String TABLE_SENDER_KEY = "sender_key";

    private final Database database;
    private final RecipientResolver resolver;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE sender_key (
                                      _id INTEGER PRIMARY KEY,
                                      recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                      device_id INTEGER NOT NULL,
                                      distribution_id BLOB NOT NULL,
                                      record BLOB NOT NULL,
                                      created_timestamp INTEGER NOT NULL,
                                      UNIQUE(recipient_id, device_id, distribution_id)
                                    );
                                    """);
        }
    }

    SenderKeyRecordStore(
            final Database database, final RecipientResolver resolver
    ) {
        this.database = database;
        this.resolver = resolver;
    }

    @Override
    public SenderKeyRecord loadSenderKey(final SignalProtocolAddress address, final UUID distributionId) {
        final var key = getKey(address, distributionId);

        try (final var connection = database.getConnection()) {
            return loadSenderKey(connection, key);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sender key store", e);
        }
    }

    @Override
    public void storeSenderKey(
            final SignalProtocolAddress address, final UUID distributionId, final SenderKeyRecord record
    ) {
        final var key = getKey(address, distributionId);

        try (final var connection = database.getConnection()) {
            storeSenderKey(connection, key, record);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender key store", e);
        }
    }

    long getCreateTimeForKey(final RecipientId selfRecipientId, final int selfDeviceId, final UUID distributionId) {
        final var sql = (
                """
                SELECT s.created_timestamp
                FROM %s AS s
                WHERE s.recipient_id = ? AND s.device_id = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, selfRecipientId.id());
                statement.setInt(2, selfDeviceId);
                statement.setBytes(3, UuidUtil.toByteArray(distributionId));
                return Utils.executeQueryForOptional(statement, res -> res.getLong("created_timestamp")).orElse(-1L);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sender key store", e);
        }
    }

    void deleteSenderKey(final RecipientId recipientId, final UUID distributionId) {
        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.recipient_id = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setBytes(2, UuidUtil.toByteArray(distributionId));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender key store", e);
        }
    }

    void deleteAll() {
        final var sql = """
                        DELETE FROM %s AS s
                        """.formatted(TABLE_SENDER_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender key store", e);
        }
    }

    void deleteAllFor(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            deleteAllFor(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender key store", e);
        }
    }

    void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var sql = """
                            UPDATE OR IGNORE %s
                            SET recipient_id = ?
                            WHERE recipient_id = ?
                            """.formatted(TABLE_SENDER_KEY);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setLong(2, toBeMergedRecipientId.id());
                final var rows = statement.executeUpdate();
                if (rows > 0) {
                    logger.debug("Reassigned {} sender keys of to be merged recipient.", rows);
                }
            }
            // Delete all conflicting sender keys now
            deleteAllFor(connection, toBeMergedRecipientId);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender key store", e);
        }
    }

    void addLegacySenderKeys(final Collection<Pair<Key, SenderKeyRecord>> senderKeys) {
        logger.debug("Migrating legacy sender keys to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var pair : senderKeys) {
                storeSenderKey(connection, pair.first(), pair.second());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sender keys store", e);
        }
        logger.debug("Complete sender keys migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    /**
     * @param identifier can be either a serialized uuid or an e164 phone number
     */
    private RecipientId resolveRecipient(String identifier) {
        return resolver.resolveRecipient(identifier);
    }

    private Key getKey(final SignalProtocolAddress address, final UUID distributionId) {
        final var recipientId = resolveRecipient(address.getName());
        return new Key(recipientId, address.getDeviceId(), distributionId);
    }

    private SenderKeyRecord loadSenderKey(final Connection connection, final Key key) throws SQLException {
        final var sql = (
                """
                SELECT s.record
                FROM %s AS s
                WHERE s.recipient_id = ? AND s.device_id = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key.recipientId().id());
            statement.setInt(2, key.deviceId());
            statement.setBytes(3, UuidUtil.toByteArray(key.distributionId()));
            return Utils.executeQueryForOptional(statement, this::getSenderKeyRecordFromResultSet).orElse(null);
        }
    }

    private void storeSenderKey(
            final Connection connection, final Key key, final SenderKeyRecord senderKeyRecord
    ) throws SQLException {
        final var sqlUpdate = """
                              UPDATE %s
                              SET record = ?
                              WHERE recipient_id = ? AND device_id = ? and distribution_id = ?
                              """.formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sqlUpdate)) {
            statement.setBytes(1, senderKeyRecord.serialize());
            statement.setLong(2, key.recipientId().id());
            statement.setLong(3, key.deviceId());
            statement.setBytes(4, UuidUtil.toByteArray(key.distributionId()));
            final var rows = statement.executeUpdate();
            if (rows > 0) {
                return;
            }
        }

        // Record doesn't exist yet, creating a new one
        final var sqlInsert = (
                """
                INSERT OR REPLACE INTO %s (recipient_id, device_id, distribution_id, record, created_timestamp)
                VALUES (?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sqlInsert)) {
            statement.setLong(1, key.recipientId().id());
            statement.setInt(2, key.deviceId());
            statement.setBytes(3, UuidUtil.toByteArray(key.distributionId()));
            statement.setBytes(4, senderKeyRecord.serialize());
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void deleteAllFor(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.recipient_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            statement.executeUpdate();
        }
    }

    private SenderKeyRecord getSenderKeyRecordFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var record = resultSet.getBytes("record");

            return new SenderKeyRecord(record);
        } catch (InvalidMessageException e) {
            logger.warn("Failed to load sender key, resetting: {}", e.getMessage());
            return null;
        }
    }

    record Key(RecipientId recipientId, int deviceId, UUID distributionId) {}
}
