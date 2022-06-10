package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.state.IdentityKeyStore.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class IdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(IdentityKeyStore.class);
    private static final String TABLE_IDENTITY = "identity";
    private final Database database;
    private final RecipientIdCreator recipientIdCreator;
    private final TrustNewIdentity trustNewIdentity;
    private final PublishSubject<RecipientId> identityChanges = PublishSubject.create();

    private boolean isRetryingDecryption = false;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE identity (
                                      _id INTEGER PRIMARY KEY,
                                      recipient_id INTEGER UNIQUE NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                      identity_key BLOB NOT NULL,
                                      added_timestamp INTEGER NOT NULL,
                                      trust_level INTEGER NOT NULL
                                    );
                                    """);
        }
    }

    public IdentityKeyStore(
            final Database database,
            final RecipientIdCreator recipientIdCreator,
            final TrustNewIdentity trustNewIdentity
    ) {
        this.database = database;
        this.recipientIdCreator = recipientIdCreator;
        this.trustNewIdentity = trustNewIdentity;
    }

    public Observable<RecipientId> getIdentityChanges() {
        return identityChanges;
    }

    public boolean saveIdentity(final RecipientId recipientId, final IdentityKey identityKey) {
        if (isRetryingDecryption) {
            return false;
        }
        try (final var connection = database.getConnection()) {
            final var identityInfo = loadIdentity(connection, recipientId);
            if (identityInfo != null && identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity already exists, not updating the trust level
                logger.trace("Not storing new identity for recipient {}, identity already stored", recipientId);
                return false;
            }

            saveNewIdentity(connection, recipientId, identityKey, identityInfo == null);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    public void setRetryingDecryption(final boolean retryingDecryption) {
        isRetryingDecryption = retryingDecryption;
    }

    public boolean setIdentityTrustLevel(RecipientId recipientId, IdentityKey identityKey, TrustLevel trustLevel) {
        try (final var connection = database.getConnection()) {
            final var identityInfo = loadIdentity(connection, recipientId);
            if (identityInfo == null) {
                logger.debug("Not updating trust level for recipient {}, identity not found", recipientId);
                return false;
            }
            if (!identityInfo.getIdentityKey().equals(identityKey)) {
                logger.debug("Not updating trust level for recipient {}, different identity found", recipientId);
                return false;
            }
            if (identityInfo.getTrustLevel() == trustLevel) {
                logger.trace("Not updating trust level for recipient {}, trust level already matches", recipientId);
                return false;
            }

            logger.debug("Updating trust level for recipient {} with trust {}", recipientId, trustLevel);
            final var newIdentityInfo = new IdentityInfo(recipientId,
                    identityKey,
                    trustLevel,
                    identityInfo.getDateAddedTimestamp());
            storeIdentity(connection, newIdentityInfo);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    public boolean isTrustedIdentity(RecipientId recipientId, IdentityKey identityKey, Direction direction) {
        if (trustNewIdentity == TrustNewIdentity.ALWAYS) {
            return true;
        }

        try (final var connection = database.getConnection()) {
            // TODO implement possibility for different handling of incoming/outgoing trust decisions
            var identityInfo = loadIdentity(connection, recipientId);
            if (identityInfo == null) {
                logger.debug("Initial identity found for {}, saving.", recipientId);
                saveNewIdentity(connection, recipientId, identityKey, true);
                identityInfo = loadIdentity(connection, recipientId);
            } else if (!identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity found, but different
                if (direction == Direction.SENDING) {
                    logger.debug("Changed identity found for {}, saving.", recipientId);
                    saveNewIdentity(connection, recipientId, identityKey, false);
                    identityInfo = loadIdentity(connection, recipientId);
                } else {
                    logger.trace("Trusting identity for {} for {}: {}", recipientId, direction, false);
                    return false;
                }
            }

            final var isTrusted = identityInfo != null && identityInfo.isTrusted();
            logger.trace("Trusting identity for {} for {}: {}", recipientId, direction, isTrusted);
            return isTrusted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public IdentityInfo getIdentityInfo(RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            return loadIdentity(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public List<IdentityInfo> getIdentities() {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    SELECT i.recipient_id, i.identity_key, i.added_timestamp, i.trust_level
                    FROM %s AS i
                    """
            ).formatted(TABLE_IDENTITY);
            try (final var statement = connection.prepareStatement(sql)) {
                return Utils.executeQueryForStream(statement, this::getIdentityInfoFromResultSet).toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public void mergeRecipients(final RecipientId recipientId, final RecipientId toBeMergedRecipientId) {
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var sql = (
                    """
                    UPDATE OR IGNORE %s
                    SET recipient_id = ?
                    WHERE recipient_id = ?
                    """
            ).formatted(TABLE_IDENTITY);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setLong(2, toBeMergedRecipientId.id());
                statement.executeUpdate();
            }

            deleteIdentity(connection, toBeMergedRecipientId);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    public void deleteIdentity(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            deleteIdentity(connection, recipientId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    void addLegacyIdentities(final Collection<IdentityInfo> identities) {
        logger.debug("Migrating legacy identities to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var identityInfo : identities) {
                storeIdentity(connection, identityInfo);
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
        logger.debug("Complete identities migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private IdentityInfo loadIdentity(
            final Connection connection, final RecipientId recipientId
    ) throws SQLException {
        final var sql = (
                """
                SELECT i.recipient_id, i.identity_key, i.added_timestamp, i.trust_level
                FROM %s AS i
                WHERE i.recipient_id = ?
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            return Utils.executeQueryForOptional(statement, this::getIdentityInfoFromResultSet).orElse(null);
        }
    }

    private void saveNewIdentity(
            final Connection connection,
            final RecipientId recipientId,
            final IdentityKey identityKey,
            final boolean firstIdentity
    ) throws SQLException {
        final var trustLevel = trustNewIdentity == TrustNewIdentity.ALWAYS || (
                trustNewIdentity == TrustNewIdentity.ON_FIRST_USE && firstIdentity
        ) ? TrustLevel.TRUSTED_UNVERIFIED : TrustLevel.UNTRUSTED;
        logger.debug("Storing new identity for recipient {} with trust {}", recipientId, trustLevel);
        final var newIdentityInfo = new IdentityInfo(recipientId, identityKey, trustLevel, System.currentTimeMillis());
        storeIdentity(connection, newIdentityInfo);
        identityChanges.onNext(recipientId);
    }

    private void storeIdentity(final Connection connection, final IdentityInfo identityInfo) throws SQLException {
        logger.trace("Storing identity info for {}, trust: {}, added: {}",
                identityInfo.getRecipientId(),
                identityInfo.getTrustLevel(),
                identityInfo.getDateAddedTimestamp());
        final var sql = (
                """
                INSERT OR REPLACE INTO %s (recipient_id, identity_key, added_timestamp, trust_level)
                VALUES (?, ?, ?, ?)
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, identityInfo.getRecipientId().id());
            statement.setBytes(2, identityInfo.getIdentityKey().serialize());
            statement.setLong(3, identityInfo.getDateAddedTimestamp());
            statement.setInt(4, identityInfo.getTrustLevel().ordinal());
            statement.executeUpdate();
        }
    }

    private void deleteIdentity(final Connection connection, final RecipientId recipientId) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s AS i
                WHERE i.recipient_id = ?
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            statement.executeUpdate();
        }
    }

    private IdentityInfo getIdentityInfoFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var recipientId = recipientIdCreator.create(resultSet.getLong("recipient_id"));
            final var id = new IdentityKey(resultSet.getBytes("identity_key"));
            final var trustLevel = TrustLevel.fromInt(resultSet.getInt("trust_level"));
            final var added = resultSet.getLong("added_timestamp");

            return new IdentityInfo(recipientId, id, trustLevel, added);
        } catch (InvalidKeyException e) {
            logger.warn("Failed to load identity key, resetting: {}", e.getMessage());
            return null;
        }
    }
}
