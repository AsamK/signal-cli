package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SenderKeySharedStore {

    private final static Logger logger = LoggerFactory.getLogger(SenderKeySharedStore.class);
    private final static String TABLE_SENDER_KEY_SHARED = "sender_key_shared";

    private final Database database;
    private final RecipientIdCreator recipientIdCreator;
    private final RecipientResolver resolver;
    private final RecipientAddressResolver addressResolver;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE sender_key_shared (
                                      _id INTEGER PRIMARY KEY,
                                      recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                      device_id INTEGER NOT NULL,
                                      distribution_id BLOB NOT NULL,
                                      timestamp INTEGER NOT NULL,
                                      UNIQUE(recipient_id, device_id, distribution_id)
                                    );
                                    """);
        }
    }

    SenderKeySharedStore(
            final Database database,
            final RecipientIdCreator recipientIdCreator,
            final RecipientAddressResolver addressResolver,
            final RecipientResolver resolver
    ) {
        this.database = database;
        this.recipientIdCreator = recipientIdCreator;
        this.addressResolver = addressResolver;
        this.resolver = resolver;
    }

    public Set<SignalProtocolAddress> getSenderKeySharedWith(final DistributionId distributionId) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    SELECT s.recipient_id, s.device_id
                    FROM %s AS s
                    WHERE s.distribution_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, UuidUtil.toByteArray(distributionId.asUuid()));
                return Utils.executeQueryForStream(statement, this::getSenderKeySharedEntryFromResultSet)
                        .map(k -> new SignalProtocolAddress(addressResolver.resolveRecipientAddress(k.recipientId())
                                .getIdentifier(), k.deviceId()))
                        .collect(Collectors.toSet());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from shared sender key store", e);
        }
    }

    public void markSenderKeySharedWith(
            final DistributionId distributionId, final Collection<SignalProtocolAddress> addresses
    ) {
        final var newEntries = addresses.stream()
                .map(a -> new SenderKeySharedEntry(resolver.resolveRecipient(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            markSenderKeysSharedWith(connection, distributionId, newEntries);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void clearSenderKeySharedWith(final Collection<SignalProtocolAddress> addresses) {
        final var entriesToDelete = addresses.stream()
                .map(a -> new SenderKeySharedEntry(resolver.resolveRecipient(a.getName()), a.getDeviceId()))
                .collect(Collectors.toSet());

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var sql = (
                    """
                    DELETE FROM %s AS s
                    WHERE recipient_id = ? AND device_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var entry : entriesToDelete) {
                    statement.setLong(1, entry.recipientId().id());
                    statement.setInt(2, entry.deviceId());
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAll() {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    DELETE FROM %s AS s
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAllFor(final RecipientId recipientId) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    DELETE FROM %s AS s
                    WHERE recipient_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteSharedWith(
            final RecipientId recipientId, final int deviceId, final DistributionId distributionId
    ) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    DELETE FROM %s AS s
                    WHERE recipient_id = ? AND device_id = ? AND distribution_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setInt(2, deviceId);
                statement.setBytes(3, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void deleteAllFor(final DistributionId distributionId) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    DELETE FROM %s AS s
                    WHERE distribution_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    public void mergeRecipients(RecipientId recipientId, RecipientId toBeMergedRecipientId) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    UPDATE OR REPLACE %s
                    SET recipient_id = ?
                    WHERE recipient_id = ?
                    """
            ).formatted(TABLE_SENDER_KEY_SHARED);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, recipientId.id());
                statement.setLong(2, toBeMergedRecipientId.id());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
    }

    void addLegacySenderKeysShared(final Map<DistributionId, Set<SenderKeySharedEntry>> sharedSenderKeys) {
        logger.debug("Migrating legacy sender keys shared to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var entry : sharedSenderKeys.entrySet()) {
                markSenderKeysSharedWith(connection, entry.getKey(), entry.getValue());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update shared sender key store", e);
        }
        logger.debug("Complete sender keys shared migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private void markSenderKeysSharedWith(
            final Connection connection, final DistributionId distributionId, final Set<SenderKeySharedEntry> newEntries
    ) throws SQLException {
        final var sql = (
                """
                INSERT OR REPLACE INTO %s (recipient_id, device_id, distribution_id, timestamp)
                VALUES (?, ?, ?, ?)
                """
        ).formatted(TABLE_SENDER_KEY_SHARED);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var entry : newEntries) {
                statement.setLong(1, entry.recipientId().id());
                statement.setInt(2, entry.deviceId());
                statement.setBytes(3, UuidUtil.toByteArray(distributionId.asUuid()));
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    private SenderKeySharedEntry getSenderKeySharedEntryFromResultSet(ResultSet resultSet) throws SQLException {
        final var recipientId = resultSet.getLong("recipient_id");
        final var deviceId = resultSet.getInt("device_id");
        return new SenderKeySharedEntry(recipientIdCreator.create(recipientId), deviceId);
    }

    record SenderKeySharedEntry(RecipientId recipientId, int deviceId) {}
}
