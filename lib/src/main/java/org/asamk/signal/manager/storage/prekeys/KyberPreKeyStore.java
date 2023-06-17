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
                INSERT INTO %s (account_id_type, key_id, serialized, is_last_resort)
                VALUES (?, ?, ?, ?)
                """
        ).formatted(TABLE_KYBER_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, keyId);
                statement.setBytes(3, record.serialize());
                statement.setBoolean(4, isLastResort);
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
}
