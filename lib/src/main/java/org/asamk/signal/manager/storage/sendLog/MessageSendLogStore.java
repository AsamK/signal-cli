package org.asamk.signal.manager.storage.sendLog;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.Database;
import org.asamk.signal.manager.storage.Utils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.push.Content;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MessageSendLogStore implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MessageSendLogStore.class);

    private static final String TABLE_MESSAGE_SEND_LOG = "message_send_log";
    private static final String TABLE_MESSAGE_SEND_LOG_CONTENT = "message_send_log_content";

    private static final Duration LOG_DURATION = Duration.ofDays(1);

    private final Database database;
    private final Thread cleanupThread;
    private final boolean sendLogDisabled;

    public MessageSendLogStore(final Database database, final boolean disableMessageSendLog) {
        this.database = database;
        this.sendLogDisabled = disableMessageSendLog;
        this.cleanupThread = new Thread(() -> {
            try {
                final var interval = Duration.ofHours(1).toMillis();
                while (!Thread.interrupted()) {
                    try (final var connection = database.getConnection()) {
                        deleteOutdatedEntries(connection);
                    } catch (SQLException e) {
                        logger.debug("MSL", e);
                        logger.warn("Deleting outdated entries failed");
                        break;
                    }
                    Thread.sleep(interval);
                }
            } catch (InterruptedException e) {
                logger.debug("Stopping msl cleanup thread");
            }
        });
        cleanupThread.setName("msl-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public static void createSql(Connection connection) throws SQLException {
        // When modifying the CREATE statement here, also add a migration in AccountDatabase.java
        try (final var statement = connection.createStatement()) {
            statement.executeUpdate("""
                                    CREATE TABLE message_send_log (
                                      _id INTEGER PRIMARY KEY,
                                      content_id INTEGER NOT NULL REFERENCES message_send_log_content (_id) ON DELETE CASCADE,
                                      address TEXT NOT NULL,
                                      device_id INTEGER NOT NULL
                                    ) STRICT;
                                    CREATE TABLE message_send_log_content (
                                      _id INTEGER PRIMARY KEY,
                                      group_id BLOB,
                                      timestamp INTEGER NOT NULL,
                                      content BLOB NOT NULL,
                                      content_hint INTEGER NOT NULL,
                                      urgent INTEGER NOT NULL
                                    ) STRICT;
                                    CREATE INDEX mslc_timestamp_index ON message_send_log_content (timestamp);
                                    CREATE INDEX msl_recipient_index ON message_send_log (address, device_id, content_id);
                                    CREATE INDEX msl_content_index ON message_send_log (content_id);
                                    """);
        }
    }

    public List<MessageSendLogEntry> findMessages(
            final ServiceId serviceId, final int deviceId, final long timestamp, final boolean isSenderKey
    ) {
        final var sql = """
                        SELECT group_id, content, content_hint, urgent
                        FROM %s l
                             INNER JOIN %s lc ON l.content_id = lc._id
                        WHERE l.address = ? AND l.device_id = ? AND lc.timestamp = ?
                        """.formatted(TABLE_MESSAGE_SEND_LOG, TABLE_MESSAGE_SEND_LOG_CONTENT);
        try (final var connection = database.getConnection()) {
            deleteOutdatedEntries(connection);

            try (final var statement = connection.prepareStatement(sql)) {
                statement.setString(1, serviceId.toString());
                statement.setInt(2, deviceId);
                statement.setLong(3, timestamp);
                try (var result = Utils.executeQueryForStream(statement, this::getMessageSendLogEntryFromResultSet)) {
                    return result.filter(Objects::nonNull)
                            .filter(e -> !isSenderKey || e.groupId().isPresent())
                            .toList();
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed read from message send log", e);
            return List.of();
        }
    }

    public long insertIfPossible(
            long sentTimestamp, SendMessageResult sendMessageResult, ContentHint contentHint, boolean urgent
    ) {
        if (sendLogDisabled) {
            return -1;
        }
        final RecipientDevices recipientDevice = getRecipientDevices(sendMessageResult);
        if (recipientDevice == null) {
            return -1;
        }

        return insert(List.of(recipientDevice),
                sentTimestamp,
                sendMessageResult.getSuccess().getContent().get(),
                contentHint,
                urgent);
    }

    public long insertIfPossible(
            long sentTimestamp, List<SendMessageResult> sendMessageResults, ContentHint contentHint, boolean urgent
    ) {
        if (sendLogDisabled) {
            return -1;
        }
        final var recipientDevices = sendMessageResults.stream()
                .map(this::getRecipientDevices)
                .filter(Objects::nonNull)
                .toList();
        if (recipientDevices.isEmpty()) {
            return -1;
        }

        final var content = sendMessageResults.stream()
                .filter(r -> r.isSuccess() && r.getSuccess().getContent().isPresent())
                .map(r -> r.getSuccess().getContent().get())
                .findFirst()
                .get();

        return insert(recipientDevices, sentTimestamp, content, contentHint, urgent);
    }

    public void addRecipientToExistingEntryIfPossible(final long contentId, final SendMessageResult sendMessageResult) {
        if (sendLogDisabled) {
            return;
        }
        final RecipientDevices recipientDevice = getRecipientDevices(sendMessageResult);
        if (recipientDevice == null) {
            return;
        }

        insertRecipientsForExistingContent(contentId, List.of(recipientDevice));
    }

    public void addRecipientToExistingEntryIfPossible(
            final long contentId, final List<SendMessageResult> sendMessageResults
    ) {
        if (sendLogDisabled) {
            return;
        }
        final var recipientDevices = sendMessageResults.stream()
                .map(this::getRecipientDevices)
                .filter(Objects::nonNull)
                .toList();
        if (recipientDevices.isEmpty()) {
            return;
        }

        insertRecipientsForExistingContent(contentId, recipientDevices);
    }

    public void deleteEntryForGroup(long sentTimestamp, GroupId groupId) {
        final var sql = """
                        DELETE FROM %s AS lc
                        WHERE lc.timestamp = ? AND lc.group_id = ?
                        """.formatted(TABLE_MESSAGE_SEND_LOG_CONTENT);
        try (final var connection = database.getConnection()) {
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, sentTimestamp);
                statement.setBytes(2, groupId.serialize());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warn("Failed delete from message send log", e);
        }
    }

    public void deleteEntryForRecipientNonGroup(long sentTimestamp, ServiceId serviceId) {
        final var sql = """
                        DELETE FROM %s AS lc
                        WHERE lc.timestamp = ? AND lc.group_id IS NULL AND lc._id IN (SELECT content_id FROM %s l WHERE l.address = ?)
                        """.formatted(TABLE_MESSAGE_SEND_LOG_CONTENT, TABLE_MESSAGE_SEND_LOG);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, sentTimestamp);
                statement.setString(2, serviceId.toString());
                statement.executeUpdate();
            }

            deleteOrphanedLogContents(connection);
            connection.commit();
        } catch (SQLException e) {
            logger.warn("Failed delete from message send log", e);
        }
    }

    public void deleteEntryForRecipient(long sentTimestamp, ServiceId serviceId, int deviceId) {
        deleteEntriesForRecipient(List.of(sentTimestamp), serviceId, deviceId);
    }

    public void deleteEntriesForRecipient(List<Long> sentTimestamps, ServiceId serviceId, int deviceId) {
        final var sql = """
                        DELETE FROM %s AS l
                        WHERE l.content_id IN (SELECT _id FROM %s lc WHERE lc.timestamp = ?) AND l.address = ? AND l.device_id = ?
                        """.formatted(TABLE_MESSAGE_SEND_LOG, TABLE_MESSAGE_SEND_LOG_CONTENT);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (final var statement = connection.prepareStatement(sql)) {
                for (final var sentTimestamp : sentTimestamps) {
                    statement.setLong(1, sentTimestamp);
                    statement.setString(2, serviceId.toString());
                    statement.setInt(3, deviceId);
                    statement.executeUpdate();
                }
            }

            deleteOrphanedLogContents(connection);
            connection.commit();
        } catch (SQLException e) {
            logger.warn("Failed delete from message send log", e);
        }
    }

    @Override
    public void close() {
        cleanupThread.interrupt();
        try {
            cleanupThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    private RecipientDevices getRecipientDevices(final SendMessageResult sendMessageResult) {
        if (sendMessageResult.isSuccess() && sendMessageResult.getSuccess().getContent().isPresent()) {
            final var serviceId = sendMessageResult.getAddress().getServiceId();
            return new RecipientDevices(serviceId, sendMessageResult.getSuccess().getDevices());
        } else {
            return null;
        }
    }

    private long insert(
            final List<RecipientDevices> recipientDevices,
            final long sentTimestamp,
            final Content content,
            final ContentHint contentHint,
            final boolean urgent
    ) {
        byte[] groupId = getGroupId(content);

        final var sql = """
                        INSERT INTO %s (timestamp, group_id, content, content_hint, urgent)
                        VALUES (?,?,?,?,?)
                        RETURNING _id
                        """.formatted(TABLE_MESSAGE_SEND_LOG_CONTENT);
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            final long contentId;
            try (final var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, sentTimestamp);
                statement.setBytes(2, groupId);
                statement.setBytes(3, content.encode());
                statement.setInt(4, contentHint.getType());
                statement.setBoolean(5, urgent);
                final var generatedKey = Utils.executeQueryForOptional(statement, Utils::getIdMapper);
                if (generatedKey.isPresent()) {
                    contentId = generatedKey.get();
                } else {
                    contentId = -1;
                }
            }
            if (contentId == -1) {
                logger.warn("Failed to insert message send log content");
                return -1;
            }
            insertRecipientsForExistingContent(contentId, recipientDevices, connection);

            connection.commit();
            return contentId;
        } catch (SQLException e) {
            logger.warn("Failed to insert into message send log", e);
            return -1;
        }
    }

    private byte[] getGroupId(final Content content) {
        try {
            return content.dataMessage == null
                    ? null
                    : content.dataMessage.group != null && content.dataMessage.group.id != null
                            ? content.dataMessage.group.id.toByteArray()
                            : content.dataMessage.groupV2 != null && content.dataMessage.groupV2.masterKey != null
                                    ? GroupUtils.getGroupIdV2(new GroupMasterKey(content.dataMessage.groupV2.masterKey.toByteArray()))
                                    .serialize()
                                    : null;
        } catch (InvalidInputException e) {
            logger.warn("Failed to parse groupId id from content");
            return null;
        }
    }

    private void insertRecipientsForExistingContent(
            final long contentId, final List<RecipientDevices> recipientDevices
    ) {
        try (final var connection = database.getConnection()) {
            connection.setAutoCommit(false);
            insertRecipientsForExistingContent(contentId, recipientDevices, connection);
            connection.commit();
        } catch (SQLException e) {
            logger.warn("Failed to append recipients to message send log", e);
        }
    }

    private void insertRecipientsForExistingContent(
            final long contentId, final List<RecipientDevices> recipientDevices, final Connection connection
    ) throws SQLException {
        final var sql = """
                        INSERT INTO %s (address, device_id, content_id)
                        VALUES (?,?,?)
                        """.formatted(TABLE_MESSAGE_SEND_LOG);
        try (final var statement = connection.prepareStatement(sql)) {
            for (final var recipientDevice : recipientDevices) {
                for (final var deviceId : recipientDevice.deviceIds()) {
                    statement.setString(1, recipientDevice.serviceId().toString());
                    statement.setInt(2, deviceId);
                    statement.setLong(3, contentId);
                    statement.executeUpdate();
                }
            }
        }
    }

    private void deleteOutdatedEntries(final Connection connection) throws SQLException {
        final var sql = """
                        DELETE FROM %s
                        WHERE timestamp < ?
                        """.formatted(TABLE_MESSAGE_SEND_LOG_CONTENT);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, System.currentTimeMillis() - LOG_DURATION.toMillis());
            final var rowCount = statement.executeUpdate();
            if (rowCount > 0) {
                logger.debug("Removed {} outdated entries from the message send log", rowCount);
            } else {
                logger.trace("No outdated entries to be removed from message send log.");
            }
        }
    }

    private void deleteOrphanedLogContents(final Connection connection) throws SQLException {
        final var sql = """
                        DELETE FROM %s
                        WHERE _id NOT IN (SELECT content_id FROM %s)
                        """.formatted(TABLE_MESSAGE_SEND_LOG_CONTENT, TABLE_MESSAGE_SEND_LOG);
        try (final var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private MessageSendLogEntry getMessageSendLogEntryFromResultSet(ResultSet resultSet) throws SQLException {
        final var groupId = Optional.ofNullable(resultSet.getBytes("group_id")).map(GroupId::unknownVersion);
        final Content content;
        try {
            content = Content.ADAPTER.decode(resultSet.getBinaryStream("content"));
        } catch (IOException e) {
            logger.warn("Failed to parse content from message send log", e);
            return null;
        }
        final var contentHint = ContentHint.fromType(resultSet.getInt("content_hint"));
        final var urgent = resultSet.getBoolean("urgent");
        return new MessageSendLogEntry(groupId, content, contentHint, urgent);
    }

    private record RecipientDevices(ServiceId serviceId, List<Integer> deviceIds) {}
}
