package org.asamk.signal.manager.storage.stickers;

import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class StickerStore {

    private final static Logger logger = LoggerFactory.getLogger(StickerStore.class);
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
                                      installed INTEGER NOT NULL DEFAULT FALSE
                                    ) STRICT;
                                    """);
        }
    }

    public StickerStore(final Database database) {
        this.database = database;
    }

    public Collection<StickerPack> getStickerPacks() {
        final var sql = (
                """
                SELECT s._id, s.pack_id, s.pack_key, s.installed
                FROM %s s
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                try (var result = Utils.executeQueryForStream(statement, this::getStickerPackFromResultSet)) {
                    return result.toList();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sticker store", e);
        }
    }

    public StickerPack getStickerPack(StickerPackId packId) {
        final var sql = (
                """
                SELECT s._id, s.pack_id, s.pack_key, s.installed
                FROM %s s
                WHERE s.pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, packId.serialize());
                return Utils.executeQueryForOptional(statement, this::getStickerPackFromResultSet).orElse(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from sticker store", e);
        }
    }

    public void addStickerPack(StickerPack stickerPack) {
        final var sql = (
                """
                INSERT INTO %s (pack_id, pack_key, installed)
                VALUES (?, ?, ?)
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, stickerPack.packId().serialize());
                statement.setBytes(2, stickerPack.packKey());
                statement.setBoolean(3, stickerPack.isInstalled());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
    }

    public void updateStickerPackInstalled(StickerPackId stickerPackId, boolean installed) {
        final var sql = (
                """
                UPDATE %s
                SET installed = ?
                WHERE pack_id = ?
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, stickerPackId.serialize());
                statement.setBoolean(2, installed);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update sticker store", e);
        }
    }

    void addLegacyStickers(Collection<StickerPack> stickerPacks) {
        logger.debug("Migrating legacy stickers to database");
        long start = System.nanoTime();
        final var sql = (
                """
                INSERT INTO %s (pack_id, pack_key, installed)
                VALUES (?, ?, ?)
                """
        ).formatted(TABLE_STICKER);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var statement = connection.prepareStatement("DELETE FROM %s".formatted(TABLE_STICKER))) {
                statement.executeUpdate();
            }
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var sticker : stickerPacks) {
                    statement.setBytes(1, sticker.packId().serialize());
                    statement.setBytes(2, sticker.packKey());
                    statement.setBoolean(3, sticker.isInstalled());
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
        return new StickerPack(internalId, StickerPackId.deserialize(packId), packKey, installed);
    }
}
