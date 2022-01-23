package org.asamk.signal.manager.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class Database implements AutoCloseable {

    private final static Logger logger = LoggerFactory.getLogger(SignalAccount.class);
    private static final long DATABASE_VERSION = 1;

    private final HikariDataSource dataSource;

    private Database(final HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database init(File databaseFile) throws SQLException {
        HikariDataSource dataSource = null;

        try {
            dataSource = getHikariDataSource(databaseFile.getAbsolutePath());

            try (final var connection = dataSource.getConnection()) {
                final var userVersion = getUserVersion(connection);
                logger.trace("Current database version: {} Program database version: {}",
                        userVersion,
                        DATABASE_VERSION);

                if (userVersion > DATABASE_VERSION) {
                    logger.error("Database has been updated by a newer signal-cli version");
                    throw new SQLException("Database has been updated by a newer signal-cli version");
                } else if (userVersion < DATABASE_VERSION) {
                    if (userVersion < 1) {
                        logger.debug("Updating database: Creating message send log tables");
                        MessageSendLogStore.createSql(connection);
                    }
                    setUserVersion(connection, DATABASE_VERSION);
                }

                final var result = new Database(dataSource);
                dataSource = null;
                return result;
            }
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() throws SQLException {
        dataSource.close();
    }

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
        sqliteConfig.setBusyTimeout(10_000);
        sqliteConfig.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        config.setDataSourceProperties(sqliteConfig.toProperties());
        config.setMinimumIdle(1);
        config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        return new HikariDataSource(config);
    }
}
