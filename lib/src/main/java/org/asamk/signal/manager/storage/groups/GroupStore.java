package org.asamk.signal.manager.storage.groups;

import com.google.protobuf.InvalidProtocolBufferException;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.groups.GroupIdV2;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientIdCreator;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupStore {

    private final static Logger logger = LoggerFactory.getLogger(GroupStore.class);
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
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed update recipient store", e);
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
        final var sql = (
                """
                DELETE FROM %s
                WHERE group_id = ?
                """
        ).formatted(TABLE_GROUP_V1);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, groupIdV1.serialize());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed update group store", e);
        }
    }

    public void deleteGroup(GroupIdV2 groupIdV2) {
        final var sql = (
                """
                DELETE FROM %s
                WHERE group_id = ?
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var connection = database.getConnection()) {
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
            if (groupId instanceof GroupIdV1 groupIdV1) {
                final var group = getGroup(connection, groupIdV1);
                if (group != null) {
                    return group;
                }
                return getGroupV2ByV1Id(connection, groupIdV1);
            } else if (groupId instanceof GroupIdV2 groupIdV2) {
                final var group = getGroup(connection, groupIdV2);
                if (group != null) {
                    return group;
                }
                return getGroupV1ByV2Id(connection, groupIdV2);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed read from group store", e);
        }
        throw new AssertionError("Invalid group id type");
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

    public List<GroupInfo> getGroups() {
        return Stream.concat(getGroupsV2().stream(), getGroupsV1().stream()).toList();
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
                logger.info("Updated {} group members when merging recipients", updatedRows);
            }
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
                            INSERT OR REPLACE INTO %s (_id, group_id, group_id_v2, name, color, expiration_time, blocked, archived)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                statement.executeUpdate();

                if (internalId == null) {
                    final var generatedKeys = statement.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        internalId = generatedKeys.getLong(1);
                    } else {
                        throw new RuntimeException("Failed to add new recipient to database");
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
                    INSERT OR REPLACE INTO %s (_id, group_id, master_key, group_data, distribution_id, blocked, distribution_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
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
                    statement.setBytes(4, groupV2.getGroup().toByteArray());
                }
                statement.setBytes(5, UuidUtil.toByteArray(groupV2.getDistributionId().asUuid()));
                statement.setBoolean(6, groupV2.isBlocked());
                statement.setBoolean(7, groupV2.isPermissionDenied());
                statement.executeUpdate();
            }
        } else {
            throw new AssertionError("Invalid group id type");
        }
    }

    private List<GroupInfoV2> getGroupsV2() {
        final var sql = (
                """
                SELECT g.group_id, g.master_key, g.group_data, g.distribution_id, g.blocked, g.permission_denied
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

    private GroupInfoV2 getGroup(Connection connection, GroupIdV2 groupIdV2) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.master_key, g.group_data, g.distribution_id, g.blocked, g.permission_denied
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V2);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV2.serialize());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV2FromResultSet).orElse(null);
        }
    }

    private GroupInfoV2 getGroupInfoV2FromResultSet(ResultSet resultSet) throws SQLException {
        try {
            final var groupId = resultSet.getBytes("group_id");
            final var masterKey = resultSet.getBytes("master_key");
            final var groupData = resultSet.getBytes("group_data");
            final var distributionId = resultSet.getBytes("distribution_id");
            final var blocked = resultSet.getBoolean("blocked");
            final var permissionDenied = resultSet.getBoolean("permission_denied");
            return new GroupInfoV2(GroupId.v2(groupId),
                    new GroupMasterKey(masterKey),
                    groupData == null ? null : DecryptedGroup.parseFrom(groupData),
                    DistributionId.from(UuidUtil.parseOrThrow(distributionId)),
                    blocked,
                    permissionDenied,
                    recipientResolver);
        } catch (InvalidInputException | InvalidProtocolBufferException e) {
            return null;
        }
    }

    private List<GroupInfoV1> getGroupsV1() {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived
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

    private GroupInfoV1 getGroup(Connection connection, GroupIdV1 groupIdV1) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived
                FROM %s g
                WHERE g.group_id = ?
                """
        ).formatted(TABLE_GROUP_V1_MEMBER, TABLE_GROUP_V1);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, groupIdV1.serialize());
            return Utils.executeQueryForOptional(statement, this::getGroupInfoV1FromResultSet).orElse(null);
        }
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
        return new GroupInfoV1(GroupId.v1(groupId),
                groupIdV2 == null ? null : GroupId.v2(groupIdV2),
                name,
                members,
                color,
                expirationTime,
                blocked,
                archived);
    }

    private GroupInfoV2 getGroupV2ByV1Id(final Connection connection, final GroupIdV1 groupId) throws SQLException {
        return getGroup(connection, GroupUtils.getGroupIdV2(groupId));
    }

    private GroupInfoV1 getGroupV1ByV2Id(Connection connection, GroupIdV2 groupIdV2) throws SQLException {
        final var sql = (
                """
                SELECT g.group_id, g.group_id_v2, g.name, g.color, (select group_concat(gm.recipient_id) from %s gm where gm.group_id = g._id) as members, g.expiration_time, g.blocked, g.archived
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
