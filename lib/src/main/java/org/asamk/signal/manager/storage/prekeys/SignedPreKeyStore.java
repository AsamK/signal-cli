package org.asamk.signal.manager.storage.prekeys;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_ARCHIVE_AGE;

public class SignedPreKeyStore implements org.signal.libsignal.protocol.state.SignedPreKeyStore {

    private static final String TABLE_SIGNED_PRE_KEY = "signed_pre_key";
    private final static Logger logger = LoggerFactory.getLogger(SignedPreKeyStore.class);

    private final Database database;
    private final int accountIdType;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE signed_pre_key (
                                      _id INTEGER PRIMARY KEY,
                                      account_id_type INTEGER NOT NULL,
                                      key_id INTEGER NOT NULL,
                                      public_key BLOB NOT NULL,
                                      private_key BLOB NOT NULL,
                                      signature BLOB NOT NULL,
                                      timestamp INTEGER DEFAULT 0,
                                      UNIQUE(account_id_type, key_id)
                                    ) STRICT;
                                    """);
        }
    }

    public SignedPreKeyStore(final Database database, final ServiceIdType serviceIdType) {
        this.database = database;
        this.accountIdType = Utils.getAccountIdType(serviceIdType);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        final SignedPreKeyRecord signedPreKeyRecord = getSignedPreKey(signedPreKeyId);
        if (signedPreKeyRecord == null) {
            throw new InvalidKeyIdException("No such signed pre key record: " + signedPreKeyId);
        }
        return signedPreKeyRecord;
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        final var sql = (
                """
                SELECT p.key_id, p.public_key, p.private_key, p.signature, p.timestamp
                FROM %s p
                WHERE p.account_id_type = ?
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                return Utils.executeQueryForStream(statement, this::getSignedPreKeyRecordFromResultSet)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from signed_pre_key store", e);
        }
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        final var sql = (
                """
                INSERT INTO %s (account_id_type, key_id, public_key, private_key, signature, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, signedPreKeyId);
                final var keyPair = record.getKeyPair();
                statement.setBytes(3, keyPair.getPublicKey().serialize());
                statement.setBytes(4, keyPair.getPrivateKey().serialize());
                statement.setBytes(5, record.getSignature());
                statement.setLong(6, record.getTimestamp());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update signed_pre_key store", e);
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return getSignedPreKey(signedPreKeyId) != null;
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ? AND p.key_id = ?
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, signedPreKeyId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update signed_pre_key store", e);
        }
    }

    public void removeAllSignedPreKeys() {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p.account_id_type = ?
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update signed_pre_key store", e);
        }
    }

    public void removeOldSignedPreKeys(int activePreKeyId) {
        final var sql = (
                """
                DELETE FROM %s AS p
                WHERE p._id IN (
                    SELECT p._id
                    FROM %s AS p
                    WHERE p.account_id_type = ?
                        AND p.key_id != ?
                        AND p.timestamp < ?
                    ORDER BY p.timestamp DESC
                    LIMIT -1 OFFSET 1
                )
                """
        ).formatted(TABLE_SIGNED_PRE_KEY, TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, activePreKeyId);
                statement.setLong(3, System.currentTimeMillis() - PREKEY_ARCHIVE_AGE);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update signed_pre_key store", e);
        }
    }

    void addLegacySignedPreKeys(final Collection<SignedPreKeyRecord> signedPreKeys) {
        logger.debug("Migrating legacy signedPreKeys to database");
        long start = System.nanoTime();
        final var sql = (
                """
                INSERT INTO %s (account_id_type, key_id, public_key, private_key, signature, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var deleteSql = "DELETE FROM %s AS p WHERE p.account_id_type = ?".formatted(TABLE_SIGNED_PRE_KEY);
            try (final var statement = connection.prepareStatement(deleteSql)) {
                statement.setInt(1, accountIdType);
                statement.executeUpdate();
            }
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var record : signedPreKeys) {
                    statement.setInt(1, accountIdType);
                    statement.setInt(2, record.getId());
                    final var keyPair = record.getKeyPair();
                    statement.setBytes(3, keyPair.getPublicKey().serialize());
                    statement.setBytes(4, keyPair.getPrivateKey().serialize());
                    statement.setBytes(5, record.getSignature());
                    statement.setLong(6, record.getTimestamp());
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update signedPreKey store", e);
        }
        logger.debug("Complete signedPreKeys migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private SignedPreKeyRecord getSignedPreKey(int signedPreKeyId) {
        final var sql = (
                """
                SELECT p.key_id, p.public_key, p.private_key, p.signature, p.timestamp
                FROM %s p
                WHERE p.account_id_type = ? AND p.key_id = ?
                """
        ).formatted(TABLE_SIGNED_PRE_KEY);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, accountIdType);
                statement.setInt(2, signedPreKeyId);
                return Utils.executeQueryForOptional(statement, this::getSignedPreKeyRecordFromResultSet).orElse(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from signed_pre_key store", e);
        }
    }

    private SignedPreKeyRecord getSignedPreKeyRecordFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var keyId = resultSet.getInt("key_id");
            final var publicKey = Curve.decodePoint(resultSet.getBytes("public_key"), 0);
            final var privateKey = Curve.decodePrivatePoint(resultSet.getBytes("private_key"));
            final var signature = resultSet.getBytes("signature");
            final var timestamp = resultSet.getLong("timestamp");
            return new SignedPreKeyRecord(keyId, timestamp, new ECKeyPair(publicKey, privateKey), signature);
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
