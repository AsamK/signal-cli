package org.asamk.signal.manager.storage.stickers;

import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalStickerPackRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StickerStore {

    private static final Logger logger = LoggerFactory.getLogger(StickerStore.class);
    private static final String TABLE_STICKER = "sticker";

    private final Database database;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE sticker (
                                      _id INTEGER PRIMARY KEY,
                                      pack_id BLOB UNIQUE NOT NULL,
                                      pack_key BLOB NOT NULL,
                                      installed INTEGER NOT NULL DEFAULT FALSE,
                                      position INTEGER NOT NULL DEFAULT 0,
                                      deleted_timestamp INTEGER NOT NULL DEFAULT 0,
                                      storage_id BLOB UNIQUE,
                                      storage_record BLOB
                                    ) STRICT;
                                    """);
        }
    }

    public StickerStore(final Database database) {
        this.database = database;
    }

    public List<StickerPack> getStickerPacks() {
        try (final var connection = database.getConnection()) {
            return getStickerPacks(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sticker store", e);
        }
    }

    public List<StickerPack> getStickerPacks(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT s._id, s.pack_id, s.pack_key, s.installed, s.position, s.deleted_timestamp, s.storage_id, s.storage_record
                FROM %s s
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            try (var result = Utils.executeQueryForStream(statement, this::getStickerPackFromResultSet)) {
                return result.toList();
            }
        }
    }

    public StickerPack getStickerPack(StickerPackId packId) {
        try (final var connection = database.getConnection()) {
            return getStickerPack(connection, packId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sticker store", e);
        }
    }

    public StickerPack getStickerPack(Connection connection, StickerPackId packId) throws SQLException {
        final var sql = (
                """
                SELECT s._id, s.pack_id, s.pack_key, s.installed, s.position, s.deleted_timestamp, s.storage_id, s.storage_record
                FROM %s s
                WHERE s.pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, packId.serialize());
            return Utils.executeQueryForOptional(statement, this::getStickerPackFromResultSet).orElse(null);
        }
    }

    public StickerPack getStickerPack(Connection connection, StorageId storageId) throws SQLException {
        final var sql = (
                """
                SELECT s._id, s.pack_id, s.pack_key, s.installed, s.position, s.deleted_timestamp, s.storage_id, s.storage_record
                FROM %s s
                WHERE s.storage_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            return Utils.executeQueryForOptional(statement, this::getStickerPackFromResultSet).orElse(null);
        }
    }

    public void addStickerPack(StickerPack stickerPack) {
        final var sql = (
                """
                INSERT INTO %s (pack_id, pack_key, installed, position, deleted_timestamp, storage_id, storage_record)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            var storageId = stickerPack.storageId();
            if (storageId == null && (stickerPack.isInstalled() || stickerPack.deletedTimestamp() > 0)) {
                storageId = StorageId.forStickerPack(KeyUtils.createRawStorageId());
            }

            final var position = stickerPack.isInstalled() ? Math.max(stickerPack.position(),
                    getNextPosition(connection)) : 0;
            var deletedTimestamp = stickerPack.deletedTimestamp();
            if (!stickerPack.isInstalled() && deletedTimestamp == 0 && storageId != null) {
                deletedTimestamp = System.currentTimeMillis();
            }

            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, stickerPack.packId().serialize());
                statement.setBytes(2, stickerPack.packKey());
                statement.setBoolean(3, stickerPack.isInstalled());
                statement.setInt(4, position);
                statement.setLong(5, deletedTimestamp);
                if (storageId == null) {
                    statement.setNull(6, Types.BLOB);
                } else {
                    statement.setBytes(6, storageId.getRaw());
                }
                if (stickerPack.storageRecord() == null) {
                    statement.setNull(7, Types.BLOB);
                } else {
                    statement.setBytes(7, stickerPack.storageRecord());
                }
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
    }

    public void updateStickerPackInstalled(StickerPackId stickerPackId, boolean installed) {
        final var sql = (
                """
                UPDATE %s
                SET installed = ?, position = ?, deleted_timestamp = ?, storage_id = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final var existing = getStickerPack(connection, stickerPackId);
            if (existing == null || existing.isInstalled() == installed) {
                connection.commit();
                return;
            }

            final var newStorageId = StorageId.forStickerPack(KeyUtils.createRawStorageId());
            final var position = installed ? getNextPosition(connection) : 0;
            final var deletedTimestamp = installed ? 0 : System.currentTimeMillis();

            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, installed);
                statement.setInt(2, position);
                statement.setLong(3, deletedTimestamp);
                statement.setBytes(4, newStorageId.getRaw());
                statement.setBytes(5, stickerPackId.serialize());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
    }

    public List<StorageId> getStorageIds(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT s.storage_id
                FROM %s s
                WHERE s.storage_id IS NOT NULL
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQueryForStream(statement, this::getStorageIdFromResultSet).toList();
        }
    }

    public void updateStorageId(
            final Connection connection,
            final StickerPackId packId,
            final StorageId storageId
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            statement.setBytes(2, packId.serialize());
            statement.executeUpdate();
        }
    }

    public void updateStorageIds(
            final Connection connection,
            final Map<StickerPackId, StorageId> storageIdMap
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var entry : storageIdMap.entrySet()) {
                statement.setBytes(1, entry.getValue().getRaw());
                statement.setBytes(2, entry.getKey().serialize());
                statement.executeUpdate();
            }
        }
    }

    public StorageId getStorageId(final Connection connection, final StickerPackId packId) throws SQLException {
        final var sql = (
                """
                SELECT s.storage_id
                FROM %s s
                WHERE s.pack_id = ? AND s.storage_id IS NOT NULL
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, packId.serialize());
            final var storageId = Utils.executeQueryForOptional(statement, this::getStorageIdFromResultSet);
            if (storageId.isPresent()) {
                return storageId.get();
            }
        }

        final var newStorageId = StorageId.forStickerPack(KeyUtils.createRawStorageId());
        updateStorageId(connection, packId, newStorageId);
        return newStorageId;
    }

    public void storeStorageRecord(
            final Connection connection,
            final StickerPackId packId,
            final StorageId storageId,
            final byte[] storageRecord
    ) throws SQLException {
        final var clearSql = (
                """
                UPDATE %s
                SET storage_id = NULL
                WHERE storage_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(clearSql)) {
            statement.setBytes(1, storageId.getRaw());
            statement.executeUpdate();
        }

        final var updateSql = (
                """
                UPDATE %s
                SET storage_id = ?, storage_record = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(updateSql)) {
            statement.setBytes(1, storageId.getRaw());
            if (storageRecord == null) {
                statement.setNull(2, Types.BLOB);
            } else {
                statement.setBytes(2, storageRecord);
            }
            statement.setBytes(3, packId.serialize());
            statement.executeUpdate();
        }
    }

    public void setMissingStorageIds() {
        final var selectSql = (
                """
                SELECT s.pack_id
                FROM %s s
                WHERE s.storage_id IS NULL AND s.installed = TRUE
                """
        ).formatted(TABLE_STICKER);
        final var updateSql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);

        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var selectStatement = connection.prepareStatement(selectSql)) {
                final var packIds = Utils.executeQueryForStream(selectStatement,
                        resultSet -> StickerPackId.deserialize(resultSet.getBytes("pack_id"))).toList();
                try (final var updateStatement = connection.prepareStatement(updateSql)) {
                    for (final var packId : packIds) {
                        updateStatement.setBytes(1, KeyUtils.createRawStorageId());
                        updateStatement.setBytes(2, packId.serialize());
                        updateStatement.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
    }

    public int removeStorageIdsFromLocalOnlyDeletedStickerPacks(
            final Connection connection,
            final Collection<StorageId> storageIds
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = NULL
                WHERE storage_id = ? AND installed = FALSE AND deleted_timestamp > 0
                """
        ).formatted(TABLE_STICKER);
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
            final SignalStickerPackRecord record
    ) throws SQLException {
        final var remote = record.getProto();
        final var packId = StickerPackId.deserialize(remote.packId.toByteArray());
        final var deleted = remote.deletedAtTimestamp > 0;
        final var packKey = remote.packKey.toByteArray();
        final var storageRecord = remote.encode();

        final var current = getStickerPack(connection, packId);

        if (current == null) {
            final var insertSql = (
                    """
                    INSERT INTO %s (pack_id, pack_key, installed, position, deleted_timestamp, storage_id, storage_record)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """
            ).formatted(TABLE_STICKER);
            try (final var statement = connection.prepareStatement(insertSql)) {
                statement.setBytes(1, packId.serialize());
                statement.setBytes(2, packKey);
                statement.setBoolean(3, !deleted);
                statement.setInt(4, deleted ? 0 : remote.position);
                statement.setLong(5, remote.deletedAtTimestamp);
                statement.setBytes(6, record.getId().getRaw());
                statement.setBytes(7, storageRecord);
                statement.executeUpdate();
            }
            return;
        }

        if (packKey.length > 0) {
            final var updateSql = (
                    """
                    UPDATE %s
                    SET pack_key = ?, installed = ?, position = ?, deleted_timestamp = ?, storage_id = ?, storage_record = ?
                    WHERE pack_id = ?
                    """
            ).formatted(TABLE_STICKER);
            try (final var statement = connection.prepareStatement(updateSql)) {
                statement.setBytes(1, packKey);
                statement.setBoolean(2, !deleted);
                statement.setInt(3, deleted ? 0 : remote.position);
                statement.setLong(4, remote.deletedAtTimestamp);
                statement.setBytes(5, record.getId().getRaw());
                statement.setBytes(6, storageRecord);
                statement.setBytes(7, packId.serialize());
                statement.executeUpdate();
            }
        } else {
            final var updateSql = (
                    """
                    UPDATE %s
                    SET installed = ?, position = ?, deleted_timestamp = ?, storage_id = ?, storage_record = ?
                    WHERE pack_id = ?
                    """
            ).formatted(TABLE_STICKER);
            try (final var statement = connection.prepareStatement(updateSql)) {
                statement.setBoolean(1, !deleted);
                statement.setInt(2, deleted ? 0 : remote.position);
                statement.setLong(3, remote.deletedAtTimestamp);
                statement.setBytes(4, record.getId().getRaw());
                statement.setBytes(5, storageRecord);
                statement.setBytes(6, packId.serialize());
                statement.executeUpdate();
            }
        }
    }

    void addLegacyStickers(Collection<StickerPack> stickerPacks) {
        logger.debug("Migrating legacy stickers to database");
        long start = System.nanoTime();
        final var sql = (
                """
                INSERT INTO %s (pack_id, pack_key, installed, position, deleted_timestamp, storage_id, storage_record)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var statement = connection.prepareStatement("DELETE FROM %s".formatted(TABLE_STICKER))) {
                statement.executeUpdate();
            }
            var installedPosition = 0;
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var sticker : stickerPacks) {
                    final var storageId = sticker.isInstalled()
                            ? StorageId.forStickerPack(KeyUtils.createRawStorageId())
                            : null;
                    statement.setBytes(1, sticker.packId().serialize());
                    statement.setBytes(2, sticker.packKey());
                    statement.setBoolean(3, sticker.isInstalled());
                    statement.setInt(4, sticker.isInstalled() ? installedPosition++ : 0);
                    statement.setLong(5, 0);
                    if (storageId == null) {
                        statement.setNull(6, Types.BLOB);
                    } else {
                        statement.setBytes(6, storageId.getRaw());
                    }
                    statement.setNull(7, Types.BLOB);
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
        logger.debug("Stickers migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private StickerPack getStickerPackFromResultSet(ResultSet resultSet) throws SQLException {
        final var internalId = resultSet.getLong("_id");
        final var packId = resultSet.getBytes("pack_id");
        final var packKey = resultSet.getBytes("pack_key");
        final var installed = resultSet.getBoolean("installed");
        final var position = resultSet.getInt("position");
        final var deletedTimestamp = resultSet.getLong("deleted_timestamp");
        final var storageIdBytes = resultSet.getBytes("storage_id");
        final var storageId = storageIdBytes == null ? null : StorageId.forStickerPack(storageIdBytes);
        final var storageRecord = resultSet.getBytes("storage_record");
        return new StickerPack(internalId,
                StickerPackId.deserialize(packId),
                packKey,
                installed,
                position,
                deletedTimestamp,
                storageId,
                storageRecord);
    }

    private StorageId getStorageIdFromResultSet(final ResultSet resultSet) throws SQLException {
        final var storageId = resultSet.getBytes("storage_id");
        return StorageId.forStickerPack(storageId);
    }

    private int getNextPosition(final Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT IFNULL(MAX(position) + 1, 0) AS next_position
                FROM %s
                WHERE installed = TRUE
                """
        ).formatted(TABLE_STICKER);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQuerySingleRow(statement, resultSet -> resultSet.getInt("next_position"));
        }
    }
}
