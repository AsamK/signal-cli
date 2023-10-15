package org.asamk.signal.manager.storage;

import com.zaxxer.hikari.HikariDataSource;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.groups.GroupStore;
import org.asamk.signal.manager.storage.identities.IdentityKeyStore;
import org.asamk.signal.manager.storage.keyValue.KeyValueStore;
import org.asamk.signal.manager.storage.prekeys.KyberPreKeyStore;
import org.asamk.signal.manager.storage.prekeys.PreKeyStore;
import org.asamk.signal.manager.storage.prekeys.SignedPreKeyStore;
import org.asamk.signal.manager.storage.recipients.CdsiStore;
import org.asamk.signal.manager.storage.recipients.RecipientStore;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogStore;
import org.asamk.signal.manager.storage.senderKeys.SenderKeyRecordStore;
import org.asamk.signal.manager.storage.senderKeys.SenderKeySharedStore;
import org.asamk.signal.manager.storage.sessions.SessionStore;
import org.asamk.signal.manager.storage.stickers.StickerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class AccountDatabase extends Database {

    private final static Logger logger = LoggerFactory.getLogger(AccountDatabase.class);
    private static final long DATABASE_VERSION = 18;

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
        StickerStore.createSql(connection);
        PreKeyStore.createSql(connection);
        SignedPreKeyStore.createSql(connection);
        KyberPreKeyStore.createSql(connection);
        GroupStore.createSql(connection);
        SessionStore.createSql(connection);
        IdentityKeyStore.createSql(connection);
        SenderKeyRecordStore.createSql(connection);
        SenderKeySharedStore.createSql(connection);
        KeyValueStore.createSql(connection);
        CdsiStore.createSql(connection);
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
                                          blocked INTEGER NOT NULL DEFAULT FALSE,
                                          archived INTEGER NOT NULL DEFAULT FALSE,
                                          profile_sharing INTEGER NOT NULL DEFAULT FALSE,

                                          profile_last_update_timestamp INTEGER NOT NULL DEFAULT 0,
                                          profile_given_name TEXT,
                                          profile_family_name TEXT,
                                          profile_about TEXT,
                                          profile_about_emoji TEXT,
                                          profile_avatar_url_path TEXT,
                                          profile_mobile_coin_address BLOB,
                                          profile_unidentified_access_mode TEXT,
                                          profile_capabilities TEXT
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 3) {
            logger.debug("Updating database: Creating sticker table");
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
        if (oldVersion < 4) {
            logger.debug("Updating database: Creating pre key tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE signed_pre_key (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          key_id INTEGER NOT NULL,
                                          public_key BLOB NOT NULL,
                                          private_key BLOB NOT NULL,
                                          signature BLOB NOT NULL,
                                          timestamp INTEGER DEFAULT 0,
                                          UNIQUE(account_id_type, key_id)
                                        ) STRICT;
                                        CREATE TABLE pre_key (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          key_id INTEGER NOT NULL,
                                          public_key BLOB NOT NULL,
                                          private_key BLOB NOT NULL,
                                          UNIQUE(account_id_type, key_id)
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 5) {
            logger.debug("Updating database: Creating group tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE group_v2 (
                                          _id INTEGER PRIMARY KEY,
                                          group_id BLOB UNIQUE NOT NULL,
                                          master_key BLOB NOT NULL,
                                          group_data BLOB,
                                          distribution_id BLOB UNIQUE NOT NULL,
                                          blocked INTEGER NOT NULL DEFAULT FALSE,
                                          permission_denied INTEGER NOT NULL DEFAULT FALSE
                                        ) STRICT;
                                        CREATE TABLE group_v1 (
                                          _id INTEGER PRIMARY KEY,
                                          group_id BLOB UNIQUE NOT NULL,
                                          group_id_v2 BLOB UNIQUE,
                                          name TEXT,
                                          color TEXT,
                                          expiration_time INTEGER NOT NULL DEFAULT 0,
                                          blocked INTEGER NOT NULL DEFAULT FALSE,
                                          archived INTEGER NOT NULL DEFAULT FALSE
                                        ) STRICT;
                                        CREATE TABLE group_v1_member (
                                          _id INTEGER PRIMARY KEY,
                                          group_id INTEGER NOT NULL REFERENCES group_v1 (_id) ON DELETE CASCADE,
                                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                          UNIQUE(group_id, recipient_id)
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 6) {
            logger.debug("Updating database: Creating session tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE session (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                          device_id INTEGER NOT NULL,
                                          record BLOB NOT NULL,
                                          UNIQUE(account_id_type, recipient_id, device_id)
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 7) {
            logger.debug("Updating database: Creating identity table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE identity (
                                          _id INTEGER PRIMARY KEY,
                                          recipient_id INTEGER UNIQUE NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                          identity_key BLOB NOT NULL,
                                          added_timestamp INTEGER NOT NULL,
                                          trust_level INTEGER NOT NULL
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 8) {
            logger.debug("Updating database: Creating sender key tables");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE sender_key (
                                          _id INTEGER PRIMARY KEY,
                                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          record BLOB NOT NULL,
                                          created_timestamp INTEGER NOT NULL,
                                          UNIQUE(recipient_id, device_id, distribution_id)
                                        ) STRICT;
                                        CREATE TABLE sender_key_shared (
                                          _id INTEGER PRIMARY KEY,
                                          recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          timestamp INTEGER NOT NULL,
                                          UNIQUE(recipient_id, device_id, distribution_id)
                                        ) STRICT;
                                        """);
            }
        }
        if (oldVersion < 9) {
            logger.debug("Updating database: Adding urgent field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        ALTER TABLE message_send_log_content ADD COLUMN urgent INTEGER NOT NULL DEFAULT TRUE;
                                        """);
            }
        }
        if (oldVersion < 10) {
            logger.debug("Updating database: Key tables on serviceId instead of recipientId");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE identity2 (
                                          _id INTEGER PRIMARY KEY,
                                          uuid BLOB UNIQUE NOT NULL,
                                          identity_key BLOB NOT NULL,
                                          added_timestamp INTEGER NOT NULL,
                                          trust_level INTEGER NOT NULL
                                        ) STRICT;
                                        INSERT INTO identity2 (_id, uuid, identity_key, added_timestamp, trust_level)
                                          SELECT i._id, r.uuid, i.identity_key, i.added_timestamp, i.trust_level
                                          FROM identity i LEFT JOIN recipient r ON i.recipient_id = r._id
                                          WHERE uuid IS NOT NULL;
                                        DROP TABLE identity;
                                        ALTER TABLE identity2 RENAME TO identity;

                                        DROP INDEX msl_recipient_index;
                                        ALTER TABLE message_send_log ADD COLUMN uuid BLOB;
                                        UPDATE message_send_log
                                          SET uuid = r.uuid
                                          FROM message_send_log i, (SELECT _id, uuid FROM recipient) AS r
                                          WHERE i.recipient_id = r._id;
                                        DELETE FROM message_send_log WHERE uuid IS NULL;
                                        ALTER TABLE message_send_log DROP COLUMN recipient_id;
                                        CREATE INDEX msl_recipient_index ON message_send_log (uuid, device_id, content_id);

                                        CREATE TABLE sender_key2 (
                                          _id INTEGER PRIMARY KEY,
                                          uuid BLOB NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          record BLOB NOT NULL,
                                          created_timestamp INTEGER NOT NULL,
                                          UNIQUE(uuid, device_id, distribution_id)
                                        ) STRICT;
                                        INSERT INTO sender_key2 (_id, uuid, device_id, distribution_id, record, created_timestamp)
                                          SELECT s._id, r.uuid, s.device_id, s.distribution_id, s.record, s.created_timestamp
                                          FROM sender_key s LEFT JOIN recipient r ON s.recipient_id = r._id
                                          WHERE uuid IS NOT NULL;
                                        DROP TABLE sender_key;
                                        ALTER TABLE sender_key2 RENAME TO sender_key;

                                        CREATE TABLE sender_key_shared2 (
                                          _id INTEGER PRIMARY KEY,
                                          uuid BLOB NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          timestamp INTEGER NOT NULL,
                                          UNIQUE(uuid, device_id, distribution_id)
                                        ) STRICT;
                                        INSERT INTO sender_key_shared2 (_id, uuid, device_id, distribution_id, timestamp)
                                          SELECT s._id, r.uuid, s.device_id, s.distribution_id, s.timestamp
                                          FROM sender_key_shared s LEFT JOIN recipient r ON s.recipient_id = r._id
                                          WHERE uuid IS NOT NULL;
                                        DROP TABLE sender_key_shared;
                                        ALTER TABLE sender_key_shared2 RENAME TO sender_key_shared;

                                        CREATE TABLE session2 (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          uuid BLOB NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          record BLOB NOT NULL,
                                          UNIQUE(account_id_type, uuid, device_id)
                                        ) STRICT;
                                        INSERT INTO session2 (_id, account_id_type, uuid, device_id, record)
                                          SELECT s._id, s.account_id_type, r.uuid, s.device_id, s.record
                                          FROM session s LEFT JOIN recipient r ON s.recipient_id = r._id
                                          WHERE uuid IS NOT NULL;
                                        DROP TABLE session;
                                        ALTER TABLE session2 RENAME TO session;
                                        """);
            }
        }
        if (oldVersion < 11) {
            logger.debug("Updating database: Adding pni field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        ALTER TABLE recipient ADD COLUMN pni BLOB;
                                        """);
            }
        }
        if (oldVersion < 12) {
            logger.debug("Updating database: Adding username field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        ALTER TABLE recipient ADD COLUMN username TEXT;
                                        """);
            }
        }
        if (oldVersion < 13) {
            logger.debug("Updating database: Cleanup unknown service ids");
            {
                final var sql = """
                                DELETE FROM identity AS i
                                WHERE i.uuid = ?
                                """;
                try (final var statement = connection.prepareStatement(sql)) {
                    statement.setBytes(1, ACI.UNKNOWN.toByteArray());
                    statement.executeUpdate();
                }
            }
            {
                final var sql = """
                                DELETE FROM sender_key_shared AS i
                                WHERE i.uuid = ?
                                """;
                try (final var statement = connection.prepareStatement(sql)) {
                    statement.setBytes(1, ACI.UNKNOWN.toByteArray());
                    statement.executeUpdate();
                }
            }
        }
        if (oldVersion < 14) {
            logger.debug("Updating database: Creating kyber_pre_key table");
            {
                try (final var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                                            CREATE TABLE kyber_pre_key (
                                                    _id INTEGER PRIMARY KEY,
                                                    account_id_type INTEGER NOT NULL,
                                                    key_id INTEGER NOT NULL,
                                                    serialized BLOB NOT NULL,
                                                    is_last_resort INTEGER NOT NULL,
                                                    UNIQUE(account_id_type, key_id)
                                            ) STRICT;
                                            """);
                }
            }
        }
        if (oldVersion < 15) {
            logger.debug("Updating database: Store serviceId as TEXT");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE tmp_mapping_table (
                                          uuid BLOB NOT NULL,
                                          address TEXT NOT NULL
                                        ) STRICT;
                                        """);

                final var sql = (
                        """
                        SELECT r.uuid, r.pni
                        FROM recipient r
                        """
                );
                final var uuidAddressMapping = new HashMap<UUID, ServiceId>();
                try (final var preparedStatement = connection.prepareStatement(sql)) {
                    try (var result = Utils.executeQueryForStream(preparedStatement, (resultSet) -> {
                        final var pni = Optional.ofNullable(resultSet.getBytes("pni"))
                                .map(UuidUtil::parseOrNull)
                                .map(ServiceId.PNI::from);
                        final var serviceIdUuid = Optional.ofNullable(resultSet.getBytes("uuid"))
                                .map(UuidUtil::parseOrNull);
                        final var serviceId = serviceIdUuid.isPresent() && pni.isPresent() && serviceIdUuid.get()
                                .equals(pni.get().getRawUuid())
                                ? pni.<ServiceId>map(p -> p)
                                : serviceIdUuid.<ServiceId>map(ACI::from);

                        return new Pair<>(serviceId, pni);
                    })) {
                        result.forEach(p -> {
                            final var serviceId = p.first();
                            final var pni = p.second();
                            if (serviceId.isPresent()) {
                                uuidAddressMapping.put(serviceId.get().getRawUuid(), serviceId.get());
                            }
                            if (pni.isPresent()) {
                                uuidAddressMapping.put(pni.get().getRawUuid(), pni.get());
                            }
                        });
                    }
                }

                final var insertSql = """
                                      INSERT INTO tmp_mapping_table (uuid, address)
                                      VALUES (?,?)
                                      """;
                try (final var insertStatement = connection.prepareStatement(insertSql)) {
                    for (final var entry : uuidAddressMapping.entrySet()) {
                        final var uuid = entry.getKey();
                        final var serviceId = entry.getValue();
                        insertStatement.setBytes(1, UuidUtil.toByteArray(uuid));
                        insertStatement.setString(2, serviceId.toString());
                        insertStatement.execute();
                    }
                }

                statement.executeUpdate("""
                                        CREATE TABLE identity2 (
                                          _id INTEGER PRIMARY KEY,
                                          address TEXT UNIQUE NOT NULL,
                                          identity_key BLOB NOT NULL,
                                          added_timestamp INTEGER NOT NULL,
                                          trust_level INTEGER NOT NULL
                                        ) STRICT;
                                        INSERT INTO identity2 (_id, address, identity_key, added_timestamp, trust_level)
                                          SELECT i._id, (SELECT t.address FROM tmp_mapping_table t WHERE t.uuid = i.uuid) address, i.identity_key, i.added_timestamp, i.trust_level
                                          FROM identity i
                                          WHERE address IS NOT NULL;
                                        DROP TABLE identity;
                                        ALTER TABLE identity2 RENAME TO identity;

                                        CREATE TABLE message_send_log2 (
                                          _id INTEGER PRIMARY KEY,
                                          content_id INTEGER NOT NULL REFERENCES message_send_log_content (_id) ON DELETE CASCADE,
                                          address TEXT NOT NULL,
                                          device_id INTEGER NOT NULL
                                        ) STRICT;
                                        INSERT INTO message_send_log2 (_id, content_id, address, device_id)
                                          SELECT m._id, m.content_id, (SELECT t.address FROM tmp_mapping_table t WHERE t.uuid = m.uuid) address, m.device_id
                                          FROM message_send_log m
                                          WHERE address IS NOT NULL;
                                        DROP INDEX msl_recipient_index;
                                        DROP INDEX msl_content_index;
                                        DROP TABLE message_send_log;
                                        ALTER TABLE message_send_log2 RENAME TO message_send_log;
                                        CREATE INDEX msl_recipient_index ON message_send_log (address, device_id, content_id);
                                        CREATE INDEX msl_content_index ON message_send_log (content_id);

                                        CREATE TABLE sender_key2 (
                                          _id INTEGER PRIMARY KEY,
                                          address TEXT NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          record BLOB NOT NULL,
                                          created_timestamp INTEGER NOT NULL,
                                          UNIQUE(address, device_id, distribution_id)
                                        ) STRICT;
                                        INSERT INTO sender_key2 (_id, address, device_id, distribution_id, record, created_timestamp)
                                          SELECT s._id, (SELECT t.address FROM tmp_mapping_table t WHERE t.uuid = s.uuid) address, s.device_id, s.distribution_id, s.record, s.created_timestamp
                                          FROM sender_key s
                                          WHERE address IS NOT NULL;
                                        DROP TABLE sender_key;
                                        ALTER TABLE sender_key2 RENAME TO sender_key;

                                        CREATE TABLE sender_key_shared2 (
                                          _id INTEGER PRIMARY KEY,
                                          address TEXT NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          distribution_id BLOB NOT NULL,
                                          timestamp INTEGER NOT NULL,
                                          UNIQUE(address, device_id, distribution_id)
                                        ) STRICT;
                                        INSERT INTO sender_key_shared2 (_id, address, device_id, distribution_id, timestamp)
                                          SELECT s._id, (SELECT t.address FROM tmp_mapping_table t WHERE t.uuid = s.uuid) address, s.device_id, s.distribution_id, s.timestamp
                                          FROM sender_key_shared s
                                          WHERE address IS NOT NULL;
                                        DROP TABLE sender_key_shared;
                                        ALTER TABLE sender_key_shared2 RENAME TO sender_key_shared;

                                        CREATE TABLE session2 (
                                          _id INTEGER PRIMARY KEY,
                                          account_id_type INTEGER NOT NULL,
                                          address TEXT NOT NULL,
                                          device_id INTEGER NOT NULL,
                                          record BLOB NOT NULL,
                                          UNIQUE(account_id_type, address, device_id)
                                        ) STRICT;
                                        INSERT INTO session2 (_id, account_id_type, address, device_id, record)
                                          SELECT s._id, s.account_id_type, (SELECT t.address FROM tmp_mapping_table t WHERE t.uuid = s.uuid) address, s.device_id, s.record
                                          FROM session s
                                          WHERE address IS NOT NULL;
                                        DROP TABLE session;
                                        ALTER TABLE session2 RENAME TO session;

                                        DROP TABLE tmp_mapping_table;
                                        """);
            }
        }
        if (oldVersion < 16) {
            logger.debug("Updating database: Adding stale_timestamp prekey field");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        ALTER TABLE pre_key ADD COLUMN stale_timestamp INTEGER;
                                        ALTER TABLE kyber_pre_key ADD COLUMN stale_timestamp INTEGER;
                                        ALTER TABLE kyber_pre_key ADD COLUMN timestamp INTEGER DEFAULT 0;
                                        """);
            }
        }
        if (oldVersion < 17) {
            logger.debug("Updating database: Adding key_value table");
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
        if (oldVersion < 18) {
            logger.debug("Updating database: Adding cdsi table");
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate("""
                                        CREATE TABLE cdsi (
                                          _id INTEGER PRIMARY KEY,
                                          number TEXT NOT NULL UNIQUE,
                                          last_seen_at INTEGER NOT NULL
                                        ) STRICT;
                                        """);
            }
        }
    }
}
