package org.asamk.signal.manager.storage;

import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UnknownStorageIdStore {

    private static final String TABLE_STORAGE_ID = "storage_id";

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE storage_id (
                                      _id INTEGER PRIMARY KEY,
                                      type INTEGER NOT NULL,
                                      storage_id BLOB UNIQUE NOT NULL
                                    ) STRICT;
                                    """);
        }
    }

    public Set<StorageId> getUnknownStorageIds(Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT s.type, s.storage_id
                FROM %s s
                """
        ).formatted(TABLE_STORAGE_ID);
        try (final var statement = connection.prepareStatement(sql)) {
            try (var result = Utils.executeQueryForStream(statement, this::getStorageIdFromResultSet)) {
                return result.collect(Collectors.toSet());
            }
        }
    }

    public List<StorageId> getUnknownStorageIds(
            Connection connection, Collection<Integer> types
    ) throws SQLException {
        final var typesCommaSeparated = types.stream().map(String::valueOf).collect(Collectors.joining(","));
        final var sql = (
                """
                SELECT s.type, s.storage_id
                FROM %s s
                WHERE s.type IN (%s)
                """
        ).formatted(TABLE_STORAGE_ID, typesCommaSeparated);
        try (final var statement = connection.prepareStatement(sql)) {
            try (var result = Utils.executeQueryForStream(statement, this::getStorageIdFromResultSet)) {
                return result.toList();
            }
        }
    }

    public void addUnknownStorageIds(Connection connection, Collection<StorageId> storageIds) throws SQLException {
        final var sql = (
                """
                INSERT OR REPLACE INTO %s (type, storage_id)
                VALUES (?, ?)
                """
        ).formatted(TABLE_STORAGE_ID);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var storageId : storageIds) {
                statement.setInt(1, storageId.getType());
                statement.setBytes(2, storageId.getRaw());
                statement.executeUpdate();
            }
        }
    }

    public void deleteUnknownStorageIds(Connection connection, Collection<StorageId> storageIds) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s
                WHERE storage_id = ?
                """
        ).formatted(TABLE_STORAGE_ID);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var storageId : storageIds) {
                statement.setBytes(1, storageId.getRaw());
                statement.executeUpdate();
            }
        }
    }

    public void deleteAllUnknownStorageIds(Connection connection) throws SQLException {
        final var sql = "DELETE FROM %s".formatted(TABLE_STORAGE_ID);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private StorageId getStorageIdFromResultSet(ResultSet resultSet) throws SQLException {
        final var type = resultSet.getInt("type");
        final var storageId = resultSet.getBytes("storage_id");
        return StorageId.forType(storageId, type);
    }
}
