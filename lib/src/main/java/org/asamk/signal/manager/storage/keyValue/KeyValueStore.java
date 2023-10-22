package org.asamk.signal.manager.storage.keyValue;

import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class KeyValueStore {

    private static final String TABLE_KEY_VALUE = "key_value";
    private final static Logger logger = LoggerFactory.getLogger(KeyValueStore.class);

    private final Database database;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE key_value (
                                      _id INTEGER PRIMARY KEY,
                                      key TEXT UNIQUE NOT NULL,
                                      value ANY
                                    ) STRICT;
                                    """);
        }
    }

    public KeyValueStore(final Database database) {
        this.database = database;
    }

    public <T> T getEntry(KeyValueEntry<T> key) {
        final var sql = (
                """
                SELECT key, value
                FROM %s p
                WHERE p.key = ?
                """
        ).formatted(TABLE_KEY_VALUE);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.key());

                final var result = Utils.executeQueryForOptional(statement,
                        resultSet -> readValueFromResultSet(key, resultSet)).orElse(null);

                if (result == null) {
                    return key.defaultValue();
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from pre_key store", e);
        }
    }

    public <T> void storeEntry(KeyValueEntry<T> key, T value) {
        final var sql = (
                """
                INSERT INTO %s (key, value)
                VALUES (?1, ?2)
                ON CONFLICT (key) DO UPDATE SET value=excluded.value
                """
        ).formatted(TABLE_KEY_VALUE);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setString(1, key.key());
                setParameterValue(statement, 2, key.clazz(), value);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update key_value store", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readValueFromResultSet(
            final KeyValueEntry<T> key, final ResultSet resultSet
    ) throws SQLException {
        Object value;
        final var clazz = key.clazz();
        if (clazz == int.class || clazz == Integer.class) {
            value = resultSet.getInt("value");
        } else if (clazz == long.class || clazz == Long.class) {
            value = resultSet.getLong("value");
        } else if (clazz == boolean.class || clazz == Boolean.class) {
            value = resultSet.getBoolean("value");
        } else if (clazz == byte[].class || clazz == Byte[].class) {
            value = resultSet.getBytes("value");
        } else if (clazz == String.class) {
            value = resultSet.getString("value");
        } else if (Enum.class.isAssignableFrom(clazz)) {
            final var name = resultSet.getString("value");
            if (name == null) {
                value = null;
            } else {
                try {
                    value = Enum.valueOf((Class<Enum>) key.clazz(), name);
                } catch (IllegalArgumentException e) {
                    logger.debug("Read invalid enum value from store, ignoring: {} for {}", name, key.clazz());
                    value = null;
                }
            }
        } else {
            throw new AssertionError("Invalid key type " + clazz.getSimpleName());
        }
        if (resultSet.wasNull()) {
            return null;
        }
        return (T) value;
    }

    private static <T> void setParameterValue(
            final PreparedStatement statement, final int parameterIndex, final Class<T> clazz, final T value
    ) throws SQLException {
        if (clazz == int.class || clazz == Integer.class) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.INTEGER);
            } else {
                statement.setInt(parameterIndex, (int) value);
            }
        } else if (clazz == long.class || clazz == Long.class) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.INTEGER);
            } else {
                statement.setLong(parameterIndex, (long) value);
            }
        } else if (clazz == boolean.class || clazz == Boolean.class) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.BOOLEAN);
            } else {
                statement.setBoolean(parameterIndex, (boolean) value);
            }
        } else if (clazz == byte[].class || clazz == Byte[].class) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.BLOB);
            } else {
                statement.setBytes(parameterIndex, (byte[]) value);
            }
        } else if (clazz == String.class) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.VARCHAR);
            } else {
                statement.setString(parameterIndex, (String) value);
            }
        } else if (Enum.class.isAssignableFrom(clazz)) {
            if (value == null) {
                statement.setNull(parameterIndex, Types.VARCHAR);
            } else {
                statement.setString(parameterIndex, ((Enum<?>) value).name());
            }
        } else {
            throw new AssertionError("Invalid key type " + clazz.getSimpleName());
        }
    }
}
