package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupStore {

    private static final Logger logger = LoggerFactory.getLogger(GroupStore.class);
    private static final String TABLE_GROUP_V2 = "group_v2";
    private static final String TABLE_GROUP_V1 = "group_v1";
    private static final String TABLE_GROUP_V1_MEMBER = "group_v1_member";

    private final Database database;
    private final RecipientResolver recipientResolver;
    private final RecipientIdCreator recipientIdCreator;

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE group_v2 (
                                      _id INTEGER PRIMARY KEY,
                                      storage_id BLOB UNIQUE,
                                      storage_record BLOB,
                                      group_id BLOB UNIQUE NOT NULL,
                                      master_key BLOB NOT NULL,
                                      group_data BLOB,
                                      distribution_id BLOB UNIQUE NOT NULL,
                                      blocked INTEGER NOT NULL DEFAULT FALSE,
                                      profile_sharing INTEGER NOT NULL DEFAULT FALSE,
                                      permission_denied INTEGER NOT NULL DEFAULT FALSE
                                    ) STRICT;
                                    CREATE TABLE group_v1 (
                                      _id INTEGER PRIMARY KEY,
                                      storage_id BLOB UNIQUE,
                                      storage_record BLOB,
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

    public GroupStore(
            final Database database,
            final RecipientResolver recipientResolver,
            final RecipientIdCreator recipientIdCreator
    ) {
        this.database = database;
        this.recipientResolver = recipientResolver;
        this.recipientIdCreator = recipientIdCreator;
    }

    public void updateGroup(GroupInfo group) {
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            updateGroup(connection, group);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
        }
    }

    public void updateGroup(final Connection connection, final GroupInfo group) throws SQLException {
        final Long internalId;
        final var sql = (
                """
                SELECT g._id
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(group instanceof GroupInfoV1 ? TABLE_GROUP_V1 : TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, group.getGroupId().serialize());
            internalId = Utils.executeQueryForOptional(statement, res -> res.getLong("_id")).orElse(null);
        }
        insertOrReplaceGroup(connection, internalId, group);
    }

    public void storeStorageRecord(
            final Connection connection, final GroupId groupId, final StorageId storageId, final byte[] storageRecord
    ) throws SQLException {
        final var groupTable = groupId instanceof GroupIdV1 ? TABLE_GROUP_V1 : TABLE_GROUP_V2;
        final var deleteSql = (
                """
                UPDATE %s
                SET storage_id = NULL
                WHERE storage_id = ?
                """
        ).formatted(groupTable);
        try (final var statement = connection.prepareStatement(deleteSql)) {
            statement.setBytes(1, storageId.getRaw());
            statement.executeUpdate();
        }
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?, storage_record = ?
                WHERE group_id = ?
                """
        ).formatted(groupTable);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            if (storageRecord == null) {
                statement.setNull(2, Types.BLOB);
            } else {
                statement.setBytes(2, storageRecord);
            }
            statement.setBytes(3, groupId.serialize());
            statement.executeUpdate();
        }
    }

    public void deleteGroup(GroupId groupId) {
        if (groupId instanceof GroupIdV1 groupIdV1) {
            deleteGroup(groupIdV1);
        } else if (groupId instanceof GroupIdV2 groupIdV2) {
            deleteGroup(groupIdV2);
        }
    }

    public void deleteGroup(GroupIdV1 groupIdV1) {
        try (final var connection = database.getConnection()) {
            deleteGroup(connection, groupIdV1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed update group store", e);
        }
    }

    private void deleteGroup(final Connection connection, final GroupIdV1 groupIdV1) throws SQLException {
        final var sql = (
                """
                DELETE FROM %s
                WHERE group_id = ?
                """
        ).formatted(TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV1.serialize());
            statement.executeUpdate();
        }
    }

    public void deleteGroup(GroupIdV2 groupIdV2) {
        try (final var connection = database.getConnection()) {
            final var sql = (
                    """
                    DELETE FROM %s
                    WHERE group_id = ?
                    """
            ).formatted(TABLE_GROUP_V2);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, groupIdV2.serialize());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update group store", e);
        }
    }

    public GroupInfo getGroup(GroupId groupId) {
        try (final var connection = database.getConnection()) {
            return getGroup(connection, groupId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
    }

    public GroupInfo getGroup(final Connection connection, final GroupId groupId) throws SQLException {
        switch (groupId) {
            case GroupIdV1 groupIdV1 -> {
                final var group = getGroup(connection, groupIdV1);
                if (group != null) {
                    return group;
                }
                return getGroupV2ByV1Id(connection, groupIdV1);
            }
            case GroupIdV2 groupIdV2 -> {
                final var group = getGroup(connection, groupIdV2);
                if (group != null) {
                    return group;
                }
                return getGroupV1ByV2Id(connection, groupIdV2);
            }
        }
    }

    public GroupInfoV1 getOrCreateGroupV1(GroupIdV1 groupId) {
        try (final var connection = database.getConnection()) {
            var group = getGroup(connection, groupId);

            if (group != null) {
                return group;
            }

            if (getGroupV2ByV1Id(connection, groupId) == null) {
                return new GroupInfoV1(groupId);
            }

            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
    }

    public GroupInfoV2 getGroupOrPartialMigrate(
            Connection connection, final GroupMasterKey groupMasterKey
    ) throws SQLException {
        final var groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
        final var groupId = GroupUtils.getGroupIdV2(groupSecretParams);

        return getGroupOrPartialMigrate(connection, groupMasterKey, groupId);
    }

    public GroupInfoV2 getGroupOrPartialMigrate(
            final GroupMasterKey groupMasterKey, final GroupIdV2 groupId
    ) {
        try (final var connection = database.getConnection()) {
            return getGroupOrPartialMigrate(connection, groupMasterKey, groupId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
    }

    private GroupInfoV2 getGroupOrPartialMigrate(
            Connection connection, final GroupMasterKey groupMasterKey, final GroupIdV2 groupId
    ) throws SQLException {
        switch (getGroup(groupId)) {
            case GroupInfoV1 groupInfoV1 -> {
                // Received a v2 group message for a v1 group, we need to locally migrate the group
                deleteGroup(connection, groupInfoV1.getGroupId());
                final var groupInfoV2 = new GroupInfoV2(groupId, groupMasterKey, recipientResolver);
                groupInfoV2.setBlocked(groupInfoV1.isBlocked());
                updateGroup(connection, groupInfoV2);
                logger.debug("Locally migrated group {} to group v2, id: {}",
                        groupInfoV1.getGroupId().toBase64(),
                        groupInfoV2.getGroupId().toBase64());
                return groupInfoV2;
            }
            case GroupInfoV2 groupInfoV2 -> {
                return groupInfoV2;
            }
            case null -> {
                return new GroupInfoV2(groupId, groupMasterKey, recipientResolver);
            }
        }
    }

    public List<GroupInfo> getGroups() {
        return Stream.concat(getGroupsV2().stream(), getGroupsV1().stream()).toList();
    }

    public List<GroupIdV1> getGroupV1Ids(Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id
                FROM %s g
                """
        ).formatted(TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQueryForStream(statement, this::getGroupIdV1FromResultSet)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public List<GroupIdV2> getGroupV2Ids(Connection connection) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id
                FROM %s g
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            return Utils.executeQueryForStream(statement, this::getGroupIdV2FromResultSet)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public void mergeRecipients(
            final Connection connection, final RecipientId recipientId, final RecipientId toBeMergedRecipientId
    ) throws SQLException {
        final var sql = (
                """
                UPDATE OR REPLACE %s
                SET recipient_id = ?
                WHERE recipient_id = ?
                """
        ).formatted(TABLE_GROUP_V1_MEMBER);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId.id());
            statement.setLong(2, toBeMergedRecipientId.id());
            final var updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                logger.debug("Updated {} group members when merging recipients", updatedRows);
            }
        }
    }

    public List<StorageId> getStorageIds(Connection connection) throws SQLException {
        final var storageIds = new ArrayList<StorageId>();
        final var sql = """
                        SELECT g.storage_id
                        FROM %s g WHERE g.storage_id IS NOT NULL
                        """;
        try (final var statement = connection.prepareStatement(sql.formatted(TABLE_GROUP_V1))) {
            Utils.executeQueryForStream(statement, this::getGroupV1StorageIdFromResultSet).forEach(storageIds::add);
        }
        try (final var statement = connection.prepareStatement(sql.formatted(TABLE_GROUP_V2))) {
            Utils.executeQueryForStream(statement, this::getGroupV2StorageIdFromResultSet).forEach(storageIds::add);
        }
        return storageIds;
    }

    public void updateStorageIds(
            Connection connection, Map<GroupIdV1, StorageId> storageIdV1Map, Map<GroupIdV2, StorageId> storageIdV2Map
    ) throws SQLException {
        final var sql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE group_id = ?
                """
        );
        try (final var statement = connection.prepareStatement(sql.formatted(TABLE_GROUP_V1))) {
            for (final var entry : storageIdV1Map.entrySet()) {
                statement.setBytes(1, entry.getValue().getRaw());
                statement.setBytes(2, entry.getKey().serialize());
                statement.executeUpdate();
            }
        }
        try (final var statement = connection.prepareStatement(sql.formatted(TABLE_GROUP_V2))) {
            for (final var entry : storageIdV2Map.entrySet()) {
                statement.setBytes(1, entry.getValue().getRaw());
                statement.setBytes(2, entry.getKey().serialize());
                statement.executeUpdate();
            }
        }
    }

    public void updateStorageId(
            Connection connection, GroupId groupId, StorageId storageId
    ) throws SQLException {
        final var sqlV1 = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE group_id = ?
                """
        ).formatted(groupId instanceof GroupIdV1 ? TABLE_GROUP_V1 : TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sqlV1)) {
            statement.setBytes(1, storageId.getRaw());
            statement.setBytes(2, groupId.serialize());
            statement.executeUpdate();
        }
    }

    public void setMissingStorageIds() {
        final var selectSql = (
                """
                SELECT g.group_id
                FROM %s g
                WHERE g.storage_id IS NULL
                """
        );
        final var updateSql = (
                """
                UPDATE %s
                SET storage_id = ?
                WHERE group_id = ?
                """
        );
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var selectStmt = connection.prepareStatement(selectSql.formatted(TABLE_GROUP_V1))) {
                final var groupIds = Utils.executeQueryForStream(selectStmt, this::getGroupIdV1FromResultSet).toList();
                try (final var updateStmt = connection.prepareStatement(updateSql.formatted(TABLE_GROUP_V1))) {
                    for (final var groupId : groupIds) {
                        updateStmt.setBytes(1, KeyUtils.createRawStorageId());
                        updateStmt.setBytes(2, groupId.serialize());
                    }
                }
            }
            try (final var selectStmt = connection.prepareStatement(selectSql.formatted(TABLE_GROUP_V2))) {
                final var groupIds = Utils.executeQueryForStream(selectStmt, this::getGroupIdV2FromResultSet).toList();
                try (final var updateStmt = connection.prepareStatement(updateSql.formatted(TABLE_GROUP_V2))) {
                    for (final var groupId : groupIds) {
                        updateStmt.setBytes(1, KeyUtils.createRawStorageId());
                        updateStmt.setBytes(2, groupId.serialize());
                        updateStmt.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update group store", e);
        }
    }

    void addLegacyGroups(final Collection<GroupInfo> groups) {
        logger.debug("Migrating legacy groups to database");
        long start = System.nanoTime();
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            for (final var group : groups) {
                insertOrReplaceGroup(connection, null, group);
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update group store", e);
        }
        logger.debug("Complete groups migration took {}ms", (System.nanoTime() - start) / 1000000);
    }

    private void insertOrReplaceGroup(
            final Connection connection, Long internalId, final GroupInfo group
    ) throws SQLException {
        if (group instanceof GroupInfoV1 groupV1) {
            if (internalId != null) {
                final var sqlDeleteMembers = "DELETE FROM %s where group_id = ?".formatted(TABLE_GROUP_V1_MEMBER);
                try (final var statement = connection.prepareStatement(sqlDeleteMembers)) {
                    statement.setLong(1, internalId);
                    statement.executeUpdate();
                }
            }
            final var sql = """
                            INSERT OR REPLACE INTO %s (_id, group_id, group_id_v2, name, color, expiration_time, blocked, archived, storage_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            RETURNING _id
                            """.formatted(TABLE_GROUP_V1);
            try (final var statement = connection.prepareStatement(sql)) {
                if (internalId == null) {
                    statement.setNull(1, Types.NUMERIC);
                } else {
                    statement.setLong(1, internalId);
                }
                statement.setBytes(2, groupV1.getGroupId().serialize());
                statement.setBytes(3, groupV1.getExpectedV2Id().serialize());
                statement.setString(4, groupV1.getTitle());
                statement.setString(5, groupV1.color);
                statement.setLong(6, groupV1.getMessageExpirationTimer());
                statement.setBoolean(7, groupV1.isBlocked());
                statement.setBoolean(8, groupV1.archived);
                statement.setBytes(9, KeyUtils.createRawStorageId());
                final var generatedKey = Utils.executeQueryForOptional(statement, Utils::getIdMapper);

                if (internalId == null) {
                    if (generatedKey.isPresent()) {
                        internalId = generatedKey.get();
                    } else {
                        throw new RuntimeException("Failed to add new group to database");
                    }
                }
            }
            final var sqlInsertMember = """
                                        INSERT OR REPLACE INTO %s (group_id, recipient_id)
                                        VALUES (?, ?)
                                        """.formatted(TABLE_GROUP_V1_MEMBER);
            try (final var statement = connection.prepareStatement(sqlInsertMember)) {
                for (final var recipient : groupV1.getMembers()) {
                    statement.setLong(1, internalId);
                    statement.setLong(2, recipient.id());
                    statement.executeUpdate();
                }
            }
        } else if (group instanceof GroupInfoV2 groupV2) {
            final var sql = (
                    """
                    INSERT OR REPLACE INTO %s (_id, group_id, master_key, group_data, distribution_id, blocked, permission_denied, storage_id, profile_sharing)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
            ).formatted(TABLE_GROUP_V2);
            try (final var statement = connection.prepareStatement(sql)) {
                if (internalId == null) {
                    statement.setNull(1, Types.NUMERIC);
                } else {
                    statement.setLong(1, internalId);
                }
                statement.setBytes(2, groupV2.getGroupId().serialize());
                statement.setBytes(3, groupV2.getMasterKey().serialize());
                if (groupV2.getGroup() == null) {
                    statement.setNull(4, Types.NUMERIC);
                } else {
                    statement.setBytes(4, groupV2.getGroup().encode());
                }
                statement.setBytes(5, UuidUtil.toByteArray(groupV2.getDistributionId().asUuid()));
                statement.setBoolean(6, groupV2.isBlocked());
                statement.setBoolean(7, groupV2.isPermissionDenied());
                statement.setBytes(8, KeyUtils.createRawStorageId());
                statement.setBoolean(9, groupV2.isProfileSharingEnabled());
                statement.executeUpdate();
            }
        } else {
            throw new AssertionError("Invalid group id type");
        }
    }

    private List<GroupInfoV2> getGroupsV2() {
        final var sql = (
                """
                SELECT g.group_id, g.master_key, g.group_data, g.distribution_id, g.blocked, g.profile_sharing, g.permission_denied, g.storage_record
                FROM %s g
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                return Utils.executeQueryForStream(statement, this::getGroupInfoV2FromResultSet)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
    }

    public GroupInfoV2 getGroup(Connection connection, GroupIdV2 groupIdV2) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.master_key, g.group_data, g.distribution_id, g.blocked, g.profile_sharing, g.permission_denied, g.storage_record
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV2.serialize());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV2FromResultSet).orElse(null);
        }
    }

    public StorageId getGroupStorageId(Connection connection, GroupIdV2 groupIdV2) throws SQLException {
        final var sql = (
                """
                SELECT g.storage_id
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV2.serialize());
            final var storageId = Utils.executeQueryForOptional(statement, this::getGroupV2StorageIdFromResultSet);
            if (storageId.isPresent()) {
                return storageId.get();
            }
        }
        final var newStorageId = StorageId.forGroupV2(KeyUtils.createRawStorageId());
        updateStorageId(connection, groupIdV2, newStorageId);
        return newStorageId;
    }

    public GroupInfoV2 getGroupV2(Connection connection, StorageId storageId) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.master_key, g.group_data, g.distribution_id, g.blocked, g.profile_sharing, g.permission_denied, g.storage_record
                FROM %s g
                WHERE g.storage_id = ?
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV2FromResultSet).orElse(null);
        }
    }

    private GroupIdV2 getGroupIdV2FromResultSet(ResultSet resultSet) throws SQLException {
        final var groupId = resultSet.getBytes("group_id");
        return GroupId.v2(groupId);
    }

    private GroupInfoV2 getGroupInfoV2FromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var groupId = resultSet.getBytes("group_id");
            final var masterKey = resultSet.getBytes("master_key");
            final var groupData = resultSet.getBytes("group_data");
            final var distributionId = resultSet.getBytes("distribution_id");
            final var blocked = resultSet.getBoolean("blocked");
            final var profileSharingEnabled = resultSet.getBoolean("profile_sharing");
            final var permissionDenied = resultSet.getBoolean("permission_denied");
            final var storageRecord = resultSet.getBytes("storage_record");
            return new GroupInfoV2(GroupId.v2(groupId),
                    new GroupMasterKey(masterKey),
                    groupData == null ? null : DecryptedGroup.ADAPTER.decode(groupData),
                    DistributionId.from(UuidUtil.parseOrThrow(distributionId)),
                    blocked,
                    profileSharingEnabled,
                    permissionDenied,
                    storageRecord,
                    recipientResolver);
        } catch (InvalidInputException | IOException e) {
            return null;
        }
    }

    private StorageId getGroupV1StorageIdFromResultSet(ResultSet resultSet) throws SQLException {
        final var storageId = resultSet.getBytes("storage_id");
        return storageId == null
                ? StorageId.forGroupV1(KeyUtils.createRawStorageId())
                : StorageId.forGroupV1(storageId);
    }

    private StorageId getGroupV2StorageIdFromResultSet(ResultSet resultSet) throws SQLException {
        final var storageId = resultSet.getBytes("storage_id");
        return storageId == null
                ? StorageId.forGroupV2(KeyUtils.createRawStorageId())
                : StorageId.forGroupV2(storageId);
    }

    private List<GroupInfoV1> getGroupsV1() {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived, g.storage_record
                FROM %s g
                """
        ).formatted(TABLE_GROUP_V1_MEMBER, TABLE_GROUP_V1);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                return Utils.executeQueryForStream(statement, this::getGroupInfoV1FromResultSet)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
    }

    public GroupInfoV1 getGroup(Connection connection, GroupIdV1 groupIdV1) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived, g.storage_record
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V1_MEMBER, TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV1.serialize());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV1FromResultSet).orElse(null);
        }
    }

    public StorageId getGroupStorageId(Connection connection, GroupIdV1 groupIdV1) throws SQLException {
        final var sql = (
                """
                SELECT g.storage_id
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV1.serialize());
            final var storageId = Utils.executeQueryForOptional(statement, this::getGroupV1StorageIdFromResultSet);
            if (storageId.isPresent()) {
                return storageId.get();
            }
        }
        final var newStorageId = StorageId.forGroupV1(KeyUtils.createRawStorageId());
        updateStorageId(connection, groupIdV1, newStorageId);
        return newStorageId;
    }

    public GroupInfoV1 getGroupV1(Connection connection, StorageId storageId) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived, g.storage_record
                FROM %s g
                WHERE g.storage_id = ?
                """
        ).formatted(TABLE_GROUP_V1_MEMBER, TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, storageId.getRaw());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV1FromResultSet).orElse(null);
        }
    }

    private GroupIdV1 getGroupIdV1FromResultSet(ResultSet resultSet) throws SQLException {
        final var groupId = resultSet.getBytes("group_id");
        return GroupId.v1(groupId);
    }

    private GroupInfoV1 getGroupInfoV1FromResultSet(ResultSet resultSet) throws SQLException {
        final var groupId = resultSet.getBytes("group_id");
        final var groupIdV2 = resultSet.getBytes("group_id_v2");
        final var name = resultSet.getString("name");
        final var color = resultSet.getString("color");
        final var membersString = resultSet.getString("members");
        final var members = membersString == null
                ? Set.<RecipientId>of()
                : Arrays.stream(membersString.split(","))
                        .map(Integer::valueOf)
                        .map(recipientIdCreator::create)
                        .collect(Collectors.toSet());
        final var expirationTime = resultSet.getInt("expiration_time");
        final var blocked = resultSet.getBoolean("blocked");
        final var archived = resultSet.getBoolean("archived");
        final var storageRecord = resultSet.getBytes("storage_record");
        return new GroupInfoV1(GroupId.v1(groupId),
                groupIdV2 == null ? null : GroupId.v2(groupIdV2),
                name,
                members,
                color,
                expirationTime,
                blocked,
                archived,
                storageRecord);
    }

    private GroupInfoV2 getGroupV2ByV1Id(final Connection connection, final GroupIdV1 groupId) throws SQLException {
        return getGroup(connection, GroupUtils.getGroupIdV2(groupId));
    }

    private GroupInfoV1 getGroupV1ByV2Id(Connection connection, GroupIdV2 groupIdV2) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived, g.storage_record
                FROM %s g
                WHERE g.group_id_v2 = ?
                """
        ).formatted(TABLE_GROUP_V1_MEMBER, TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV2.serialize());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV1FromResultSet).orElse(null);
        }
    }
}
