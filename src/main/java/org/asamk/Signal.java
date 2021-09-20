package org.asamk;

import org.asamk.signal.dbus.DbusAttachment;
import org.asamk.signal.dbus.DbusMention;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DBus interface for the org.asamk.Signal service.
 * Including emitted Signals and returned Errors.
 */
public interface Signal extends DBusInterface {

    long sendMessage(
            String message, List<String> attachmentNames, String recipient
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessage(
            String message, List<String> attachmentNames, List<String> recipients
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    void sendTyping(
            String recipient, boolean stop
    ) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity;

    void sendReadReceipt(
            String recipient, List<Long> targetSentTimestamp
    ) throws Error.Failure, Error.UntrustedIdentity;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, String recipient
    ) throws Error.Failure, Error.InvalidNumber;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws Error.Failure, Error.InvalidNumber;

    long sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, byte[] groupId
    ) throws Error.Failure, Error.GroupNotFound, Error.InvalidGroupId;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String recipient
    ) throws Error.InvalidNumber, Error.Failure;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws Error.InvalidNumber, Error.Failure;

    long sendNoteToSelfMessage(
            String message, List<String> attachmentNames
    ) throws Error.AttachmentInvalid, Error.Failure;

    void sendEndSessionMessage(List<String> recipients) throws Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendGroupMessage(
            String message, List<String> attachmentNames, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid, Error.InvalidGroupId;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber, Error.InvalidGroupId;

    String getContactName(String number) throws Error.InvalidNumber;

    void setContactName(String number, String name) throws Error.InvalidNumber;

    void setContactBlocked(String number, boolean blocked) throws Error.InvalidNumber;

    void setGroupBlocked(byte[] groupId, boolean blocked) throws Error.GroupNotFound, Error.InvalidGroupId;

    List<byte[]> getGroupIds();

    String getGroupName(byte[] groupId) throws Error.InvalidGroupId;

    List<String> getGroupMembers(byte[] groupId) throws Error.InvalidGroupId;

    boolean isRegistered();

    byte[] updateGroup(
            byte[] groupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    void updateProfile(
            String name, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    String version();

    List<String> listNumbers();

    List<String> getContactNumber(final String name) throws Error.Failure;

    void quitGroup(final byte[] groupId) throws Error.GroupNotFound, Error.Failure, Error.InvalidGroupId;

    boolean isContactBlocked(final String number) throws Error.InvalidNumber;

    boolean isGroupBlocked(final byte[] groupId) throws Error.InvalidGroupId;

    boolean isMember(final byte[] groupId) throws Error.InvalidGroupId;

    byte[] joinGroup(final String groupLink) throws Error.Failure;

    class MessageReceived extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final byte[] groupId;
        private final String message;
        private final List<String> attachmentNames;

        public MessageReceived(
                String objectpath,
                long timestamp,
                String sender,
                byte[] groupId,
                String message,
                List<String> attachmentNames
        ) throws DBusException {
            super(objectpath, timestamp, sender, groupId, message, attachmentNames);
            this.timestamp = timestamp;
            this.sender = sender;
            this.groupId = groupId;
            this.message = message;
            this.attachmentNames = attachmentNames;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getAttachmentNames() {
            return attachmentNames;
        }
    }

    class MessageReceivedV2 extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final byte[] groupId;
        private final String message;
        private final List<DbusMention> mentions;
        private final List<DbusAttachment> attachments;

        public MessageReceivedV2(
                String objectpath,
                long timestamp,
                String sender,
                byte[] groupId,
                String message,
                List<DbusMention> mentions,
                List<DbusAttachment> attachments
        ) throws DBusException {
            super(objectpath, timestamp, sender, groupId, message, mentions, attachments);
            this.timestamp = timestamp;
            this.sender = sender;
            this.groupId = groupId;
            this.message = message;
            this.mentions = mentions;
            this.attachments = attachments;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<DbusMention> getMentions() {
            return mentions;
        }

        public List<DbusAttachment> getAttachments() {
            return attachments;
        }
    }

    class ReceiptReceived extends DBusSignal {

        private final long timestamp;
        private final String sender;

        public ReceiptReceived(String objectpath, long timestamp, String sender) throws DBusException {
            super(objectpath, timestamp, sender);
            this.timestamp = timestamp;
            this.sender = sender;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }
    }

    class SyncMessageReceived extends DBusSignal {

        private final long timestamp;
        private final String source;
        private final String destination;
        private final byte[] groupId;
        private final String message;
        private final List<String> attachmentNames;

        public SyncMessageReceived(
                String objectpath,
                long timestamp,
                String source,
                String destination,
                byte[] groupId,
                String message,
                List<String> attachmentNames
        ) throws DBusException {
            super(objectpath, timestamp, source, destination, groupId, message, attachmentNames);
            this.timestamp = timestamp;
            this.source = source;
            this.destination = destination;
            this.groupId = groupId;
            this.message = message;
            this.attachmentNames = attachmentNames;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getAttachmentNames() {
            return attachmentNames;
        }
    }

    class SyncMessageReceivedV2 extends DBusSignal {

        private final long timestamp;
        private final String source;
        private final String destination;
        private final byte[] groupId;
        private final String message;
        private final List<DbusMention> mentions;
        private final List<DbusAttachment> attachments;

        public SyncMessageReceivedV2(
                String objectpath,
                long timestamp,
                String source,
                String destination,
                byte[] groupId,
                String message,
                List<DbusMention> mentions,
                List<DbusAttachment> attachments
        ) throws DBusException {
            super(objectpath, timestamp, source, destination, groupId, message, mentions, attachments);
            this.timestamp = timestamp;
            this.source = source;
            this.destination = destination;
            this.groupId = groupId;
            this.message = message;
            this.mentions = mentions;
            this.attachments = attachments;
        }
        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<DbusMention> getMentions() {
            return mentions;
        }

        public List<DbusAttachment> getAttachments() {
            return attachments;
        }
    }

    interface Error {

        class AttachmentInvalid extends DBusExecutionException {

            public AttachmentInvalid(final String message) {
                super(message);
            }
        }

        class Failure extends DBusExecutionException {

            public Failure(final String message) {
                super(message);
            }
        }

        class GroupNotFound extends DBusExecutionException {

            public GroupNotFound(final String message) {
                super(message);
            }
        }

        class InvalidGroupId extends DBusExecutionException {

            public InvalidGroupId(final String message) {
                super(message);
            }
        }

        class InvalidNumber extends DBusExecutionException {

            public InvalidNumber(final String message) {
                super(message);
            }
        }

        class UntrustedIdentity extends DBusExecutionException {

            public UntrustedIdentity(final String message) {
                super(message);
            }
        }
    }
}
