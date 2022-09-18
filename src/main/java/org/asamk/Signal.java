package org.asamk;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

/**
 * DBus interface for the org.asamk.Signal service.
 * Including emitted Signals and returned Errors.
 */
public interface Signal extends DBusInterface {

    String getSelfNumber();

    void subscribeReceive();

    void unsubscribeReceive();

    long sendMessage(
            String message, List<String> attachments, String recipient
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessage(
            String message, List<String> attachments, List<String> recipients
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    void sendTyping(
            String recipient, boolean stop
    ) throws Error.Failure, Error.UntrustedIdentity;

    void sendReadReceipt(
            String recipient, List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity;

    void sendViewedReceipt(
            String recipient, List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, String recipient
    ) throws Error.Failure, Error.InvalidNumber;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws Error.Failure, Error.InvalidNumber;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String recipient
    ) throws Error.InvalidNumber, Error.Failure;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws Error.InvalidNumber, Error.Failure;

    long sendPaymentNotification(byte[] receipt, String note, String recipient) throws Error.Failure;

    void sendContacts() throws Error.Failure;

    void sendSyncRequest() throws Error.Failure;

    long sendNoteToSelfMessage(
            String message, List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure;

    void sendEndSessionMessage(List<String> recipients) throws Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    void deleteRecipient(final String recipient) throws Error.Failure;

    void deleteContact(final String recipient) throws Error.Failure;

    long sendGroupMessage(
            String message, List<String> attachments, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid, Error.InvalidGroupId;

    void sendGroupTyping(
            final byte[] groupId, final boolean stop
    ) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity;

    long sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, byte[] groupId
    ) throws Error.Failure, Error.GroupNotFound, Error.InvalidGroupId;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber, Error.InvalidGroupId;

    String getContactName(String number) throws Error.InvalidNumber;

    void setContactName(String number, String name) throws Error.InvalidNumber;

    void setExpirationTimer(final String number, final int expiration) throws Error.Failure;

    void setContactBlocked(String number, boolean blocked) throws Error.InvalidNumber;

    @Deprecated
    void setGroupBlocked(byte[] groupId, boolean blocked) throws Error.GroupNotFound, Error.InvalidGroupId;

    @Deprecated
    List<byte[]> getGroupIds();

    DBusPath getGroup(byte[] groupId);

    List<StructGroup> listGroups();

    @Deprecated
    String getGroupName(byte[] groupId) throws Error.InvalidGroupId;

    @Deprecated
    List<String> getGroupMembers(byte[] groupId) throws Error.InvalidGroupId;

    byte[] createGroup(
            String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber;

    @Deprecated
    byte[] updateGroup(
            byte[] groupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    @Deprecated
    boolean isRegistered() throws Error.Failure, Error.InvalidNumber;

    boolean isRegistered(String number) throws Error.Failure, Error.InvalidNumber;

    List<Boolean> isRegistered(List<String> numbers) throws Error.Failure, Error.InvalidNumber;

    void addDevice(String uri) throws Error.InvalidUri;

    DBusPath getDevice(long deviceId);

    List<StructDevice> listDevices() throws Error.Failure;

    DBusPath getThisDevice();

    void updateProfile(
            String givenName,
            String familyName,
            String about,
            String aboutEmoji,
            String avatarPath,
            boolean removeAvatar
    ) throws Error.Failure;

    void updateProfile(
            String name, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    void removePin();

    void setPin(String registrationLockPin);

    String version();

    List<String> listNumbers();

    List<String> getContactNumber(final String name) throws Error.Failure;

    @Deprecated
    void quitGroup(final byte[] groupId) throws Error.GroupNotFound, Error.Failure, Error.InvalidGroupId;

    boolean isContactBlocked(final String number) throws Error.InvalidNumber;

    @Deprecated
    boolean isGroupBlocked(final byte[] groupId) throws Error.InvalidGroupId;

    @Deprecated
    boolean isMember(final byte[] groupId) throws Error.InvalidGroupId;

    byte[] joinGroup(final String groupLink) throws Error.Failure;

    String uploadStickerPack(String stickerPackPath) throws Error.Failure;

    void submitRateLimitChallenge(String challenge, String captchaString) throws Error.Failure;

    void unregister() throws Error.Failure;

    void deleteAccount() throws Error.Failure;

    class MessageReceivedV2 extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final byte[] groupId;
        private final String message;
        private final Map<String, Variant<?>> extras;

        public MessageReceivedV2(
                String objectpath,
                long timestamp,
                String sender,
                byte[] groupId,
                String message,
                final Map<String, Variant<?>> extras
        ) throws DBusException {
            super(objectpath, timestamp, sender, groupId, message, extras);
            this.timestamp = timestamp;
            this.sender = sender;
            this.groupId = groupId;
            this.message = message;
            this.extras = extras;
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

        public Map<String, Variant<?>> getExtras() {
            return extras;
        }
    }

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

    class ReceiptReceivedV2 extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final String type;
        private final Map<String, Variant<?>> extras;

        public ReceiptReceivedV2(
                String objectpath,
                long timestamp,
                String sender,
                final String type,
                final Map<String, Variant<?>> extras
        ) throws DBusException {
            super(objectpath, timestamp, sender, type, extras);
            this.timestamp = timestamp;
            this.sender = sender;
            this.type = type;
            this.extras = extras;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }

        public String getReceiptType() {
            return type;
        }

        public Map<String, Variant<?>> getExtras() {
            return extras;
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

    class SyncMessageReceivedV2 extends DBusSignal {

        private final long timestamp;
        private final String source;
        private final String destination;
        private final byte[] groupId;
        private final String message;
        private final Map<String, Variant<?>> extras;

        public SyncMessageReceivedV2(
                String objectpath,
                long timestamp,
                String source,
                String destination,
                byte[] groupId,
                String message,
                final Map<String, Variant<?>> extras
        ) throws DBusException {
            super(objectpath, timestamp, source, destination, groupId, message, extras);
            this.timestamp = timestamp;
            this.source = source;
            this.destination = destination;
            this.groupId = groupId;
            this.message = message;
            this.extras = extras;
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

        public Map<String, Variant<?>> getExtras() {
            return extras;
        }
    }

    class StructDevice extends Struct {

        @Position(0)
        DBusPath objectPath;

        @Position(1)
        Long id;

        @Position(2)
        String name;

        public StructDevice(final DBusPath objectPath, final Long id, final String name) {
            this.objectPath = objectPath;
            this.id = id;
            this.name = name;
        }

        public DBusPath getObjectPath() {
            return objectPath;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @DBusProperty(name = "Id", type = Integer.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Name", type = String.class)
    @DBusProperty(name = "Created", type = String.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "LastSeen", type = String.class, access = DBusProperty.Access.READ)
    interface Device extends DBusInterface, Properties {

        void removeDevice() throws Error.Failure;
    }

    @DBusProperty(name = "ReadReceipts", type = Boolean.class)
    @DBusProperty(name = "UnidentifiedDeliveryIndicators", type = Boolean.class)
    @DBusProperty(name = "TypingIndicators", type = Boolean.class)
    @DBusProperty(name = "LinkPreviews", type = Boolean.class)
    interface Configuration extends DBusInterface, Properties {}

    class StructGroup extends Struct {

        @Position(0)
        DBusPath objectPath;

        @Position(1)
        byte[] id;

        @Position(2)
        String name;

        public StructGroup(final DBusPath objectPath, final byte[] id, final String name) {
            this.objectPath = objectPath;
            this.id = id;
            this.name = name;
        }

        public DBusPath getObjectPath() {
            return objectPath;
        }

        public byte[] getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @DBusProperty(name = "Id", type = Byte[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Name", type = String.class)
    @DBusProperty(name = "Description", type = String.class)
    @DBusProperty(name = "Avatar", type = String.class, access = DBusProperty.Access.WRITE)
    @DBusProperty(name = "IsBlocked", type = Boolean.class)
    @DBusProperty(name = "IsMember", type = Boolean.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "IsAdmin", type = Boolean.class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "MessageExpirationTimer", type = Integer.class)
    @DBusProperty(name = "Members", type = String[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "PendingMembers", type = String[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "RequestingMembers", type = String[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Admins", type = String[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "Banned", type = String[].class, access = DBusProperty.Access.READ)
    @DBusProperty(name = "PermissionAddMember", type = String.class)
    @DBusProperty(name = "PermissionEditDetails", type = String.class)
    @DBusProperty(name = "PermissionSendMessage", type = String.class)
    @DBusProperty(name = "GroupInviteLink", type = String.class, access = DBusProperty.Access.READ)
    interface Group extends DBusInterface, Properties {

        void quitGroup() throws Error.Failure, Error.LastGroupAdmin;

        void deleteGroup() throws Error.Failure;

        void addMembers(List<String> recipients) throws Error.Failure;

        void removeMembers(List<String> recipients) throws Error.Failure;

        void addAdmins(List<String> recipients) throws Error.Failure;

        void removeAdmins(List<String> recipients) throws Error.Failure;

        void resetLink() throws Error.Failure;

        void disableLink() throws Error.Failure;

        void enableLink(boolean requiresApproval) throws Error.Failure;
    }

    interface Error {

        class AttachmentInvalid extends DBusExecutionException {

            public AttachmentInvalid(final String message) {
                super("Invalid attachment: " + message);
            }
        }

        class InvalidUri extends DBusExecutionException {

            public InvalidUri(final String message) {
                super("Invalid uri: " + message);
            }
        }

        class Failure extends DBusExecutionException {

            public Failure(final Exception e) {
                super("Failure: " + e.getMessage() + " (" + e.getClass().getSimpleName() + ")");
            }

            public Failure(final String message) {
                super("Failure: " + message);
            }
        }

        class DeviceNotFound extends DBusExecutionException {

            public DeviceNotFound(final String message) {
                super("Device not found: " + message);
            }
        }

        class GroupNotFound extends DBusExecutionException {

            public GroupNotFound(final String message) {
                super("Group not found: " + message);
            }
        }

        class InvalidGroupId extends DBusExecutionException {

            public InvalidGroupId(final String message) {
                super("Invalid group id: " + message);
            }
        }

        class LastGroupAdmin extends DBusExecutionException {

            public LastGroupAdmin(final String message) {
                super("Last group admin: " + message);
            }
        }

        class InvalidNumber extends DBusExecutionException {

            public InvalidNumber(final String message) {
                super("Invalid number: " + message);
            }
        }

        class UntrustedIdentity extends DBusExecutionException {

            public UntrustedIdentity(final String message) {
                super("Untrusted identity: " + message);
            }
        }

        class UnregisteredRecipient extends DBusExecutionException {

            public UnregisteredRecipient(final String message) {
                super("Unregistered recipient: " + message);
            }
        }
    }
}
