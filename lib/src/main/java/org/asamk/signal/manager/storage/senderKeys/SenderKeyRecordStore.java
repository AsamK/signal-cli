package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.groups.state.SenderKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;
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

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE sender_key (
                                      _id INTEGER PRIMARY KEY,
                                      address TEXT NOT NULL,
                                      device_id INTEGER NOT NULL,
                                      distribution_id BLOB NOT NULL,
                                      record BLOB NOT NULL,
                                      created_timestamp INTEGER NOT NULL,
                                      UNIQUE(address, device_id, distribution_id)
                                    ) STRICT;
                                    """);
        }
    }

    SenderKeyRecordStore(final Database database) {
        this.database = database;
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

    long getCreateTimeForKey(final ServiceId selfServiceId, final int selfDeviceId, final UUID distributionId) {
        final var sql = (
                """
                SELECT s.created_timestamp
                FROM %s AS s
                WHERE s.address = ? AND s.device_id = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setString(1, selfServiceId.toString());
                statement.setInt(2, selfDeviceId);
                statement.setBytes(3, UuidUtil.toByteArray(distributionId));
                return Utils.executeQueryForOptional(statement, res -> res.getLong("created_timestamp")).orElse(-1L);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sender key store", e);
        }
    }

    void deleteSenderKey(final ServiceId serviceId, final UUID distributionId) {
        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.address = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setString(1, serviceId.toString());
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

    void deleteAllFor(final ServiceId serviceId) {
        try (final var connection = database.getConnection()) {
            deleteAllFor(connection, serviceId);
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

    private Key getKey(final SignalProtocolAddress address, final UUID distributionId) {
        return new Key(address.getName(), address.getDeviceId(), distributionId);
    }

    private SenderKeyRecord loadSenderKey(final Connection connection, final Key key) throws SQLException {
        final var sql = (
                """
                SELECT s.record
                FROM %s AS s
                WHERE s.address = ? AND s.device_id = ? AND s.distribution_id = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.address());
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
                              WHERE address = ? AND device_id = ? and distribution_id = ?
                              """.formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sqlUpdate)) {
            statement.setBytes(1, senderKeyRecord.serialize());
            statement.setString(2, key.address());
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
                INSERT OR REPLACE INTO %s (address, device_id, distribution_id, record, created_timestamp)
                VALUES (?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sqlInsert)) {
            statement.setString(1, key.address());
            statement.setInt(2, key.deviceId());
            statement.setBytes(3, UuidUtil.toByteArray(key.distributionId()));
            statement.setBytes(4, senderKeyRecord.serialize());
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void deleteAllFor(final Connection connection, final ServiceId serviceId) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s AS s
                WHERE s.address = ?
                """
        ).formatted(TABLE_SENDER_KEY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, serviceId.toString());
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

    record Key(String address, int deviceId, UUID distributionId) {}
}
