package org.asamk.signal.manager.storage.identities;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.api.TrustNewIdentity;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.state.IdentityKeyStore.Direction;
import org.signal.libsignal.protocol.state.IdentityKeyStore.IdentityChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class IdentityKeyStore {

    private static final Logger logger = LoggerFactory.getLogger(IdentityKeyStore.class);
    private static final String TABLE_IDENTITY = "identity";
    private final Database database;
    private final TrustNewIdentity trustNewIdentity;
    private final RecipientStore recipientStore;
    private final PublishSubject<ServiceId> identityChanges = PublishSubject.create();

    private boolean isRetryingDecryption = false;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE identity (
                                      _id INTEGER PRIMARY KEY,
                                      address TEXT UNIQUE NOT NULL,
                                      identity_key BLOB NOT NULL,
                                      added_timestamp INTEGER NOT NULL,
                                      trust_level INTEGER NOT NULL
                                    ) STRICT;
                                    """);
        }
    }

    public IdentityKeyStore(
            final Database database,
            final TrustNewIdentity trustNewIdentity,
            RecipientStore recipientStore
    ) {
        this.database = database;
        this.trustNewIdentity = trustNewIdentity;
        this.recipientStore = recipientStore;
    }

    public Observable<ServiceId> getIdentityChanges() {
        return identityChanges;
    }

    public IdentityChange saveIdentity(final ServiceId serviceId, final IdentityKey identityKey) {
        return saveIdentity(serviceId.toString(), identityKey);
    }

    public IdentityChange saveIdentity(
            final Connection connection,
            final ServiceId serviceId,
            final IdentityKey identityKey
    ) throws SQLException {
        return saveIdentity(connection, serviceId.toString(), identityKey);
    }

    IdentityChange saveIdentity(final String address, final IdentityKey identityKey) {
        if (isRetryingDecryption) {
            return IdentityChange.NEW_OR_UNCHANGED;
        }
        try (final var connection = database.getConnection()) {
            return saveIdentity(connection, address, identityKey);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    private IdentityChange saveIdentity(
            final Connection connection,
            final String address,
            final IdentityKey identityKey
    ) throws SQLException {
        final var identityInfo = loadIdentity(connection, address);
        if (identityInfo == null) {
            saveNewIdentity(connection, address, identityKey, true);
            return IdentityChange.NEW_OR_UNCHANGED;
        }
        if (identityInfo.getIdentityKey().equals(identityKey)) {
            // Identity already exists, not updating the trust level
            logger.trace("Not storing new identity for recipient {}, identity already stored", address);
            return IdentityChange.NEW_OR_UNCHANGED;
        }

        saveNewIdentity(connection, address, identityKey, false);
        return IdentityChange.REPLACED_EXISTING;
    }

    public void setRetryingDecryption(final boolean retryingDecryption) {
        isRetryingDecryption = retryingDecryption;
    }

    public boolean setIdentityTrustLevel(ServiceId serviceId, IdentityKey identityKey, TrustLevel trustLevel) {
        try (final var connection = database.getConnection()) {
            return setIdentityTrustLevel(connection, serviceId, identityKey, trustLevel);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update identity store", e);
        }
    }

    public boolean setIdentityTrustLevel(
            final Connection connection,
            final ServiceId serviceId,
            final IdentityKey identityKey,
            final TrustLevel trustLevel
    ) throws SQLException {
        final var address = serviceId.toString();
        final var identityInfo = loadIdentity(connection, address);
        if (identityInfo == null) {
            logger.debug("Not updating trust level for recipient {}, identity not found", serviceId);
            return false;
        }
        if (!identityInfo.getIdentityKey().equals(identityKey)) {
            logger.debug("Not updating trust level for recipient {}, different identity found", serviceId);
            return false;
        }
        if (identityInfo.getTrustLevel() == trustLevel) {
            logger.trace("Not updating trust level for recipient {}, trust level already matches", serviceId);
            return false;
        }

        logger.debug("Updating trust level for recipient {} with trust {}", serviceId, trustLevel);
        final var newIdentityInfo = new IdentityInfo(address,
                identityKey,
                trustLevel,
                identityInfo.getDateAddedTimestamp());
        storeIdentity(connection, newIdentityInfo);
        return true;
    }

    public boolean isTrustedIdentity(ServiceId serviceId, IdentityKey identityKey, Direction direction) {
        return isTrustedIdentity(serviceId.toString(), identityKey, direction);
    }

    public boolean isTrustedIdentity(String address, IdentityKey identityKey, Direction direction) {
        if (trustNewIdentity == TrustNewIdentity.ALWAYS) {
            return true;
        }

        try (final var connection = database.getConnection()) {
            // TODO implement possibility for different handling of incoming/outgoing trust decisions
            var identityInfo = loadIdentity(connection, address);
            if (identityInfo == null) {
                logger.debug("Initial identity found for {}, saving.", address);
                saveNewIdentity(connection, address, identityKey, true);
                identityInfo = loadIdentity(connection, address);
            } else if (!identityInfo.getIdentityKey().equals(identityKey)) {
                // Identity found, but different
                if (direction == Direction.SENDING) {
                    logger.debug("Changed identity found for {}, saving.", address);
                    saveNewIdentity(connection, address, identityKey, false);
                    identityInfo = loadIdentity(connection, address);
                } else {
                    logger.trace("Trusting identity for {} for {}: {}", address, direction, false);
                    return false;
                }
            }

            final var isTrusted = identityInfo != null && identityInfo.isTrusted();
            logger.trace("Trusting identity for {} for {}: {}", address, direction, isTrusted);
            return isTrusted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public IdentityInfo getIdentityInfo(ServiceId serviceId) {
        return getIdentityInfo(serviceId.toString());
    }

    public IdentityInfo getIdentityInfo(String address) {
        try (final var connection = database.getConnection()) {
            return loadIdentity(connection, address);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public IdentityInfo getIdentityInfo(Connection connection, String address) throws SQLException {
        return loadIdentity(connection, address);
    }

    public List<IdentityInfo> getIdentities() {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    SELECT i.address, i.identity_key, i.added_timestamp, i.trust_level
                    FROM %s AS i
                    """
            ).formatted(TABLE_IDENTITY);
            try (final var statement = connection.prepareStatement(sql)) {
                return Utils.executeQueryForStream(statement, this::getIdentityInfoFromResultSet)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from identity store", e);
        }
    }

    public void deleteIdentity(final ServiceId serviceId) {
        try (final var connection = database.getConnection()) {
            deleteIdentity(connection, serviceId.toString());
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

    private IdentityInfo loadIdentity(final Connection connection, final String address) throws SQLException {
        final var sql = (
                """
                SELECT i.address, i.identity_key, i.added_timestamp, i.trust_level
                FROM %s AS i
                WHERE i.address = ?
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, address);
            return Utils.executeQueryForOptional(statement, this::getIdentityInfoFromResultSet).orElse(null);
        }
    }

    private void saveNewIdentity(
            final Connection connection,
            final String address,
            final IdentityKey identityKey,
            final boolean firstIdentity
    ) throws SQLException {
        final var trustLevel = trustNewIdentity == TrustNewIdentity.ALWAYS || (
                trustNewIdentity == TrustNewIdentity.ON_FIRST_USE && firstIdentity
        ) ? TrustLevel.TRUSTED_UNVERIFIED : TrustLevel.UNTRUSTED;
        logger.debug("Storing new identity for recipient {} with trust {}", address, trustLevel);
        final var newIdentityInfo = new IdentityInfo(address, identityKey, trustLevel, System.currentTimeMillis());
        storeIdentity(connection, newIdentityInfo);
        final var serviceId = ServiceId.parseOrNull(address);
        if (serviceId != null) {
            identityChanges.onNext(serviceId);
        }
    }

    private void storeIdentity(final Connection connection, final IdentityInfo identityInfo) throws SQLException {
        logger.trace("Storing identity info for {}, trust: {}, added: {}",
                identityInfo.getServiceId(),
                identityInfo.getTrustLevel(),
                identityInfo.getDateAddedTimestamp());
        final var sql = (
                """
                INSERT OR REPLACE INTO %s (address, identity_key, added_timestamp, trust_level)
                VALUES (?, ?, ?, ?)
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, identityInfo.getAddress());
            statement.setBytes(2, identityInfo.getIdentityKey().serialize());
            statement.setLong(3, identityInfo.getDateAddedTimestamp());
            statement.setInt(4, identityInfo.getTrustLevel().ordinal());
            statement.executeUpdate();
        }
        recipientStore.rotateStorageId(connection, identityInfo.getServiceId());
    }

    private void deleteIdentity(final Connection connection, final String address) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s AS i
                WHERE i.address = ?
                """
        ).formatted(TABLE_IDENTITY);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setString(1, address);
            statement.executeUpdate();
        }
    }

    private IdentityInfo getIdentityInfoFromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var address = resultSet.getString("address");
            final var id = new IdentityKey(resultSet.getBytes("identity_key"));
            final var trustLevel = TrustLevel.fromInt(resultSet.getInt("trust_level"));
            final var added = resultSet.getLong("added_timestamp");

            return new IdentityInfo(address, id, trustLevel, added);
        } catch (InvalidKeyException e) {
            logger.warn("Failed to load identity key, resetting: {}", e.getMessage());
            return null;
        }
    }
}
