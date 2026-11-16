package org.asamk.signal.manager.storage.notificationProfiles;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.util.KeyUtils;
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NotificationProfileStore {

    private static final String TABLE_NOTIFICATION_PROFILE = "notification_profile";

    private final Database database;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE notification_profile (
                                      _id INTEGER PRIMARY KEY,
                                      profile_id BLOB UNIQUE NOT NULL,
                                      name TEXT NOT NULL,
                                      deleted_timestamp INTEGER NOT NULL DEFAULT 0,
                                      storage_id BLOB UNIQUE,
                                      storage_record BLOB
                                    ) STRICT;
                                    """);
        }
    }

    public NotificationProfileStore(final Database database) {
        this.database = database;
    }

    public List<NotificationProfile> getNotificationProfiles() {
        try (final var connection = database.getConnection()) {
            return getNotificationProfiles(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from notification profile store", e);
        }
    }

    public List<NotificationProfile> getNotificationProfiles(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT n._id, n.profile_id, n.name, n.deleted_timestamp, n.storage_id, n.storage_record
                FROM %s n
                ORDER BY n._id
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQueryForStream(statement, this::getNotificationProfileFromResultSet).toList();
        }
    }

    public NotificationProfile getNotificationProfile(final Connection connection, final byte[] profileId) throws SQLException {
        final var sql = (
                """
                SELECT n._id, n.profile_id, n.name, n.deleted_timestamp, n.storage_id, n.storage_record
                FROM %s n
                WHERE n.profile_id = ?
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, profileId);
            return Utils.executeQueryForOptional(statement, this::getNotificationProfileFromResultSet).orElse(null);
        }
    }

    public NotificationProfile getNotificationProfile(final Connection connection, final StorageId storageId) throws SQLException {
        final var sql = (
                """
                SELECT n._id, n.profile_id, n.name, n.deleted_timestamp, n.storage_id, n.storage_record
                FROM %s n
                WHERE n.storage_id = ?
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            return Utils.executeQueryForOptional(statement, this::getNotificationProfileFromResultSet).orElse(null);
        }
    }

    public List<StorageId> getStorageIds(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT n.storage_id
                FROM %s n
                WHERE n.storage_id IS NOT NULL
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQueryForStream(statement, this::getStorageIdFromResultSet).toList();
        }
    }

    public void updateStorageId(
            final Connection connection,
            final byte[] profileId,
            final StorageId storageId
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE profile_id = ?
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            statement.setBytes(2, profileId);
            statement.executeUpdate();
        }
    }

    public void updateStorageIds(
            final Connection connection,
            final Map<Long, StorageId> storageIdMap
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var entry : storageIdMap.entrySet()) {
                statement.setBytes(1, entry.getValue().getRaw());
                statement.setLong(2, entry.getKey());
                statement.executeUpdate();
            }
        }
    }

    public void setMissingStorageIds() {
        final var selectSql = (
                """
                SELECT n._id
                FROM %s n
                WHERE n.storage_id IS NULL
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        final var updateSql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE _id = ?
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var selectStatement = connection.prepareStatement(selectSql)) {
                final var ids = Utils.executeQueryForStream(selectStatement, Utils::getIdMapper).toList();
                try (final var updateStatement = connection.prepareStatement(updateSql)) {
                    for (final var id : ids) {
                        updateStatement.setBytes(1, KeyUtils.createRawStorageId());
                        updateStatement.setLong(2, id);
                        updateStatement.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update notification profile store", e);
        }
    }

    /**
     * Remove profiles that were deleted remotely and are only kept locally as deletion tombstones.
     */
    public int removeLocalOnlyDeletedNotificationProfiles(
            final Connection connection,
            final Collection<StorageId> storageIds
    ) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s
                WHERE storage_id = ? AND deleted_timestamp > 0
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        var count = 0;
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var storageId : storageIds) {
                statement.setBytes(1, storageId.getRaw());
                count += statement.executeUpdate();
            }
        }
        return count;
    }

    public void upsertFromStorageSync(
            final Connection connection,
            final SignalNotificationProfileRecord record
    ) throws SQLException {
        final var remote = record.getProto();
        final var profileId = remote.id.toByteArray();
        final var storageRecord = remote.encode();

        final var sql = (
                """
                INSERT INTO %s (profile_id, name, deleted_timestamp, storage_id, storage_record)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (profile_id) DO UPDATE SET
                  name = excluded.name,
                  deleted_timestamp = excluded.deleted_timestamp,
                  storage_id = excluded.storage_id,
                  storage_record = excluded.storage_record
                """
        ).formatted(TABLE_NOTIFICATION_PROFILE);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, profileId);
            statement.setString(2, remote.name);
            statement.setLong(3, remote.deletedAtTimestampMs);
            statement.setBytes(4, record.getId().getRaw());
            statement.setBytes(5, storageRecord);
            statement.executeUpdate();
        }
    }

    private NotificationProfile getNotificationProfileFromResultSet(ResultSet resultSet) throws SQLException {
        final var internalId = resultSet.getLong("_id");
        final var profileId = resultSet.getBytes("profile_id");
        final var name = resultSet.getString("name");
        final var deletedTimestamp = resultSet.getLong("deleted_timestamp");
        final var storageIdBytes = resultSet.getBytes("storage_id");
        final var storageId = storageIdBytes == null ? null : StorageId.forNotificationProfile(storageIdBytes);
        final var storageRecord = resultSet.getBytes("storage_record");
        return new NotificationProfile(internalId, profileId, name, deletedTimestamp, storageId, storageRecord);
    }

    private StorageId getStorageIdFromResultSet(final ResultSet resultSet) throws SQLException {
        final var storageId = resultSet.getBytes("storage_id");
        return StorageId.forNotificationProfile(storageId);
    }
}
