package org.asamk;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;

import java.util.List;

/**
 * DBus interface for the org.asamk.Signal service.
 * Including emitted Signals and returned Errors.
 */
public interface Signal extends DBusInterface {

    String getSelfNumber();

    long sendMessage(
            String message, List<String> attachments, String recipient
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    List<String> getGroupAdminMembers(final byte[] groupId);

    List<byte[]> getGroupIds();

    byte[] getGroupId(String groupName) throws Error.GroupNotFound;

    List<String> getGroupIdStrings();

    String getGroupIdString(String groupName) throws Error.GroupNotFound;

    String getGroupInviteUri(byte[] groupId);

    List<String> getGroupMembers(byte[] groupId) throws Error.InvalidGroupId;

    String getGroupName(byte[] groupId) throws Error.InvalidGroupId;

    List<String> getGroupNames();

    List<String> getGroupPendingMembers(final byte[] groupId);

    List<String> getGroupRequestingMembers(final byte[] groupId);

    boolean isAdmin(final byte[] groupId);

    boolean isGroupAnnounceOnly(byte[] groupId);

    boolean isGroupBlocked(final byte[] groupId) throws Error.InvalidGroupId;

    boolean isMember(final byte[] groupId) throws Error.InvalidGroupId;

    byte[] joinGroup(final String groupLink) throws Error.Failure;

    void quitGroup(final byte[] groupId) throws Error.GroupNotFound, Error.Failure, Error.InvalidGroupId;

    long sendGroupMessage(
            String message, List<String> attachments, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid, Error.InvalidGroupId;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber, Error.InvalidGroupId;

    long sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, byte[] groupId
    ) throws Error.Failure, Error.GroupNotFound, Error.InvalidGroupId;

    void setGroupAnnounceOnly(byte[] groupId, boolean isAnnouncementGroup);

    void setExpirationTimer(final String number, final int expiration) throws Error.Failure;

    void setGroupBlocked(byte[] groupId, boolean blocked) throws Error.GroupNotFound, Error.InvalidGroupId;

    List<String> updateAdmins(final byte[] groupId, List<String>admins, boolean addToAdmins) throws Error.GroupNotFound, Error.InvalidGroupId, Error.Failure;

    byte[] updateGroup(
            byte[] groupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    byte[] updateGroup(
            byte[] groupId, String name, String description, List<String> addMembers, List<String> removeMembers, List<String> addAdmins, List<String> removeAdmins, boolean resetGroupLink, String groupLinkState, String addMemberPermission, String editDetailsPermission, String avatar, Integer expirationTimer, Boolean isAnnouncementGroup
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    byte[] updateGroup(
            byte[] groupId, String name, String description, List<String> addMembers, List<String> removeMembers, List<String> addAdmins, List<String> removeAdmins, boolean resetGroupLink, String groupLinkState, String addMemberPermission, String editDetailsPermission, String avatar, Integer expirationTimer
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    List<String> updateMembers(final byte[] groupId, List<String> members, boolean addToMembers);

    void sendContacts() throws Error.Failure;

    void sendSyncRequest() throws Error.Failure;

    DBusPath getDevice(long deviceId);

    List<DBusPath> listDevices() throws Error.Failure;

    DBusPath getThisDevice();

    void updateProfile(
            String givenName,
            String familyName,
            String about,
            String aboutEmoji,
            String avatarPath,
            boolean removeAvatar
    ) throws Error.Failure;

    void addDevice(String uri) throws Error.Failure, Error.InvalidUri;

    void setContactBlocked(final String number, final boolean blocked) throws Error.Failure;

    boolean isRegistered();
    boolean isRegistered(String number);
    List<Boolean> isRegistered(List<String> numbers) throws Error.Failure;

    void removePin();

    void setPin(String registrationLockPin);

    String version();

    String getContactName(String number) throws Error.InvalidNumber;

    List<String> getContactNumber(final String name) throws Error.Failure;

    boolean isContactBlocked(final String number) throws Error.InvalidNumber;

    void sendEndSessionMessage(List<String> recipients) throws Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessage(
            String message, List<String> attachments, List<String> recipients
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String recipient
    ) throws Error.InvalidNumber, Error.Failure;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws Error.InvalidNumber, Error.Failure;

    void sendReadReceipt(
            String recipient, List<Long> targetSentTimestamp
    ) throws Error.Failure, Error.UntrustedIdentity;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, String recipient
    ) throws Error.Failure, Error.InvalidNumber;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws Error.Failure, Error.InvalidNumber;

    void setContactName(String number, String name) throws Error.InvalidNumber;

    List<String> listNumbers();

    long sendNoteToSelfMessage(
            String message, List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure;

    void sendTyping(boolean typingAction, List<String> groupIdStrings, List<String> numbers) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity;

    void updateProfile(
            String name, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    String uploadStickerPack(String stickerPackPath) throws Error.Failure;

    class MessageReceived extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final byte[] groupId;
        private final String message;
        private final List<String> attachments;

        public MessageReceived(
                String objectpath,
                long timestamp,
                String sender,
                byte[] groupId,
                String message,
                List<String> attachments
        ) throws DBusException {
            super(objectpath, timestamp, sender, groupId, message, attachments);
            this.timestamp = timestamp;
            this.sender = sender;
            this.groupId = groupId;
            this.message = message;
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

        public List<String> getAttachments() {
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
        private final List<String> attachments;

        public SyncMessageReceived(
                String objectpath,
                long timestamp,
                String source,
                String destination,
                byte[] groupId,
                String message,
                List<String> attachments
        ) throws DBusException {
            super(objectpath, timestamp, source, destination, groupId, message, attachments);
            this.timestamp = timestamp;
            this.source = source;
            this.destination = destination;
            this.groupId = groupId;
            this.message = message;
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

        public List<String> getAttachments() {
            return attachments;
        }
    }

    @DBusProperty(name = "Id", type = Integer.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Name", type = String.class)
    @DBusProperty(name = "Created", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "LastSeen", type = String.class, access = DBusProperty.Access.READ)
    interface Device extends DBusInterface, Properties {

        void removeDevice() throws Error.Failure;

        String getDeviceName() throws Error.Failure;

        void setDeviceName(String deviceName) throws Error.Failure;

    }

    interface Error {

        class AttachmentInvalid extends DBusExecutionException {

            public AttachmentInvalid(final String message) {
                super(message);
            }
        }

        class InvalidUri extends DBusExecutionException {

            public InvalidUri(final String message) {
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
