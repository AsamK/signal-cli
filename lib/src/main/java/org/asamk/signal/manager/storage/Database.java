package org.asamk.signal.manager.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

public abstract class Database implements AutoCloseable {

    private final Logger logger;
    private final long databaseVersion;
    private final HikariDataSource dataSource;

    protected Database(final Logger logger, final long databaseVersion, final HikariDataSource dataSource) {
        this.logger = logger;
        this.databaseVersion = databaseVersion;
        this.dataSource = dataSource;
    }

    public static <T extends Database> T initDatabase(
            File databaseFile, Function<HikariDataSource, T> newDatabase
    ) throws SQLException {
        HikariDataSource dataSource = null;

        try {
            dataSource = getHikariDataSource(databaseFile.getAbsolutePath());

            final var result = newDatabase.apply(dataSource);
            result.initDb();
            dataSource = null;
            return result;
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    public final Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() throws SQLException {
        dataSource.close();
    }

    protected final void initDb() throws SQLException {
        try (final var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            final var userVersion = getUserVersion(connection);
            logger.trace("Current database version: {} Program database version: {}", userVersion, databaseVersion);

            if (userVersion == 0) {
                createDatabase(connection);
                setUserVersion(connection, databaseVersion);
            } else if (userVersion > databaseVersion) {
                logger.error("Database has been updated by a newer signal-cli version");
                throw new SQLException("Database has been updated by a newer signal-cli version");
            } else if (userVersion < databaseVersion) {
                upgradeDatabase(connection, userVersion);
                setUserVersion(connection, databaseVersion);
            }
            connection.commit();
        }
    }

    protected abstract void createDatabase(final Connection connection) throws SQLException;

    protected abstract void upgradeDatabase(final Connection connection, long oldVersion) throws SQLException;

    private static long getUserVersion(final Connection connection) throws SQLException {
        try (final var statement = connection.createStatement()) {
            final var resultSet = statement.executeQuery("PRAGMA user_version");
            return resultSet.getLong(1);
        }
    }

    private static void setUserVersion(final Connection connection, long userVersion) throws SQLException {
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = " + userVersion);
        }
    }

    private static HikariDataSource getHikariDataSource(final String databaseFile) {
        final var sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(60_000);
        sqliteConfig.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        config.setDataSourceProperties(sqliteConfig.toProperties());
        config.setMinimumIdle(1);
        config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return new HikariDataSource(config);
    }
}
