package org.asamk.signal.manager.storage.prekeys;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceKyberPreKeyStore;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_ARCHIVE_AGE;

public class KyberPreKeyStore implements SignalServiceKyberPreKeyStore {

    private static final String TABLE_KYBER_PRE_KEY = "kyber_pre_key";
    private final static Logger logger = LoggerFactory.getLogger(KyberPreKeyStore.class);

    private final Database database;
    private final int accountIdType;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE kyber_pre_key (
                                      _id INTEGER PRIMARY KEY,
                                      account_id_type INTEGER NOT NULL,
                                      key_id INTEGER NOT NULL,
                                      serialized BLOB NOT NULL,
                                      is_last_resort INTEGER NOT NULL,
                                      stale_timestamp INTEGER,
                                      timestamp INTEGER DEFAULT 0,
                                      UNIQUE(account_id_type, key_id)
                                    ) STRICT;
                                    """);
        }
    }

    public KyberPreKeyStore(final Database database, final ServiceIdType serviceIdType) {
        this.database = database;
        this.accountIdType = Utils.getAccountIdType(serviceIdType);
    }

    @Override
    public KyberPreKeyRecord loadKyberPreKey(final int keyId) throws InvalidKeyIdException {
        final var kyberPreKey = getPreKey(keyId);
        if (kyberPreKey == null) {
            throw new InvalidKeyIdException("No such kyber pre key record: " + keyId);
        }
        return kyberPreKey;
    }

    @Override
    public List<KyberPreKeyRecord> loadKyberPreKeys() {
        final var sql = (
                """
                SELECT p.serialized
                FROM %s p
                WHERE p.account_id_type = ?
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                return Utils.executeQueryForStream(statement, this::getKyberPreKeyRecordFromResultSet).toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from kyber_pre_key store", e);
        }
    }

    @Override
    public List<KyberPreKeyRecord> loadLastResortKyberPreKeys() {
        final var sql = (
                """
                SELECT p.serialized
                FROM %s p
                WHERE p.account_id_type = ? AND p.is_last_resort = TRUE
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                return Utils.executeQueryForStream(statement, this::getKyberPreKeyRecordFromResultSet).toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from kyber_pre_key store", e);
        }
    }

    @Override
    public void storeLastResortKyberPreKey(final int keyId, final KyberPreKeyRecord record) {
        storeKyberPreKey(keyId, record, true);
    }

    @Override
    public void storeKyberPreKey(final int keyId, final KyberPreKeyRecord record) {
        storeKyberPreKey(keyId, record, false);
    }

    public void storeKyberPreKey(final int keyId, final KyberPreKeyRecord record, final boolean isLastResort) {
        final var sql = (
                """
                INSERT INTO %s (account_id_type, key_id, serialized, is_last_resort, timestamp)
                VALUES (?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, keyId);
                statement.setBytes(3, record.serialize());
                statement.setBoolean(4, isLastResort);
                statement.setLong(5, record.getTimestamp());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    @Override
    public boolean containsKyberPreKey(final int keyId) {
        return getPreKey(keyId) != null;
    }

    @Override
    public void markKyberPreKeyUsed(final int keyId) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ? AND p.key_id = ? AND p.is_last_resort = FALSE
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, keyId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    @Override
    public void removeKyberPreKey(final int keyId) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ? AND p.key_id = ?
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, keyId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    public void removeAllKyberPreKeys() {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ?
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    private KyberPreKeyRecord getPreKey(int keyId) {
        final var sql = (
                """
                SELECT p.serialized
                FROM %s p
                WHERE p.account_id_type = ? AND p.key_id = ?
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, keyId);
                return Utils.executeQueryForOptional(statement, this::getKyberPreKeyRecordFromResultSet).orElse(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from kyber_pre_key store", e);
        }
    }

    private KyberPreKeyRecord getKyberPreKeyRecordFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var serialized = resultSet.getBytes("serialized");
            return new KyberPreKeyRecord(serialized);
        } catch (InvalidMessageException e) {
            return null;
        }
    }

    public void removeOldLastResortKyberPreKeys(int activeLastResortKyberPreKeyId) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p._id IN (
                    SELECT p._id
                    FROM %s AS p
                    WHERE p.account_id_type = ?
                        AND p.is_last_resort = TRUE
                        AND p.key_id != ?
                        AND p.timestamp < ?
                    ORDER BY p.timestamp DESC
                    LIMIT -1 OFFSET 1
                )
                """
        ).formatted(TABLE_KYBER_PRE_KEY, TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, activeLastResortKyberPreKeyId);
                statement.setLong(3, System.currentTimeMillis() - PREKEY_ARCHIVE_AGE);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    @Override
    public void deleteAllStaleOneTimeKyberPreKeys(final long threshold, final int minCount) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ?1
                    AND p.stale_timestamp < ?2
                    AND p.is_last_resort = FALSE
                    AND p._id NOT IN (
                        SELECT _id
                        FROM %s p2
                        WHERE p2.account_id_type = ?1
                        ORDER BY
                          CASE WHEN p2.stale_timestamp IS NULL THEN 1 ELSE 0 END DESC,
                          p2.stale_timestamp DESC,
                          p2._id DESC
                        LIMIT ?3
                    )
                """
        ).formatted(TABLE_KYBER_PRE_KEY, TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setLong(2, threshold);
                statement.setInt(3, minCount);
                final var rowCount = statement.executeUpdate();
                if (rowCount > 0) {
                    logger.debug("Deleted {} stale one time kyber pre keys", rowCount);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }

    @Override
    public void markAllOneTimeKyberPreKeysStaleIfNecessary(final long staleTime) {
        final var sql = (
                """
                UPDATE %s
                SET stale_timestamp = ?
                WHERE account_id_type = ? AND stale_timestamp IS NULL AND is_last_resort = FALSE
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, staleTime);
                statement.setInt(2, accountIdType);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update kyber_pre_key store", e);
        }
    }
}
