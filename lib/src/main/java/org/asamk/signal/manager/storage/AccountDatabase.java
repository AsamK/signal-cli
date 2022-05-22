package org.asamk.signal.manager.storage;

import com.zaxxer.hikari.HikariDataSource;

import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class AccountDatabase extends Database {

    private final static Logger logger = LoggerFactory.getLogger(AccountDatabase.class);
    private static final long DATABASE_VERSION = 2;

    private AccountDatabase(final HikariDataSource dataSource) {
        super(logger, DATABASE_VERSION, dataSource);
    }

    public static AccountDatabase init(File databaseFile) throws SQLException {
        return initDatabase(databaseFile, AccountDatabase::new);
    }

    @Override
    protected void createDatabase(final Connection connection) throws SQLException {
        RecipientStore.createSql(connection);
        MessageSendLogStore.createSql(connection);
    }

    @Override
    protected void upgradeDatabase(final Connection connection, final long oldVersion) throws SQLException {
        if (oldVersion < 2) {
            logger.debug("Updating database: Creating recipient table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE recipient (
                                          _id INTEGER PRIMARY KEY AUTOINCREMENT,
                                          number TEXT UNIQUE,
                                          uuid BLOB UNIQUE,
                                          profile_key BLOB,
                                          profile_key_credential BLOB,

                                          given_name TEXT,
                                          family_name TEXT,
                                          color TEXT,

                                          expiration_time INTEGER NOT NULL DEFAULT 0,
                                          blocked BOOLEAN NOT NULL DEFAULT FALSE,
                                          archived BOOLEAN NOT NULL DEFAULT FALSE,
                                          profile_sharing BOOLEAN NOT NULL DEFAULT FALSE,

                                          profile_last_update_timestamp INTEGER NOT NULL DEFAULT 0,
                                          profile_given_name TEXT,
                                          profile_family_name TEXT,
                                          profile_about TEXT,
                                          profile_about_emoji TEXT,
                                          profile_avatar_url_path TEXT,
                                          profile_mobile_coin_address BLOB,
                                          profile_unidentified_access_mode TEXT,
                                          profile_capabilities TEXT
                                        );
                                        """);
            }
        }
    }
}
