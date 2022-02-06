package org.asamk.signal.manager.storage;

import com.zaxxer.hikari.HikariDataSource;

import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class AccountDatabase extends Database {

    private final static Logger logger = LoggerFactory.getLogger(AccountDatabase.class);
    private static final long DATABASE_VERSION = 1;

    private AccountDatabase(final HikariDataSource dataSource) {
        super(logger, DATABASE_VERSION, dataSource);
    }

    public static AccountDatabase init(File databaseFile) throws SQLException {
        return initDatabase(databaseFile, AccountDatabase::new);
    }

    @Override
    protected void upgradeDatabase(final Connection connection, final long oldVersion) throws SQLException {
        if (oldVersion < 1) {
            logger.debug("Updating database: Creating message send log tables");
            MessageSendLogStore.createSql(connection);
        }
    }
}
