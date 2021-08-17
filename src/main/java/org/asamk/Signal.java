package org.asamk;

import org.asamk.SignalControl;
import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.groups.GroupId;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * DBus interface for the org.asamk.Signal service.
 * Including emitted Signals and returned Errors.
 */
public interface Signal extends DBusInterface {

    long sendMessage(
            String message, List<String> attachments, String recipient
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessage(
            String message, List<String> attachments, List<String> recipients
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, String recipient
    ) throws Error.Failure, Error.InvalidNumber;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws Error.Failure, Error.InvalidNumber;

    long sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, byte[] groupId
    ) throws Error.Failure, Error.GroupNotFound;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String recipient
    ) throws Error.InvalidNumber, Error.Failure;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws Error.InvalidNumber, Error.Failure;

    long sendNoteToSelfMessage(
            String message, List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure;

    void sendEndSessionMessage(List<String> recipients) throws Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendGroupMessage(
            String message, List<String> attachments, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber;

    String getContactName(String number) throws Error.InvalidNumber;

    void setContactName(String number, String name) throws Error.InvalidNumber;

    void setExpirationTimer(final String number, final int expiration) throws Error.InvalidNumber;

    void setContactBlocked(String number, boolean blocked) throws Error.InvalidNumber;

    void setGroupBlocked(byte[] groupId, boolean blocked) throws Error.GroupNotFound;

    List<byte[]> getGroupIds();

    String getGroupName(byte[] groupId);

    List<String> getGroupMembers(byte[] groupId);

    byte[] updateGroup(
            byte[] groupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    String updateGroup(
            String base64GroupId,
            String name,
            String description,
            List<String> addMembers,
            List<String> removeMembers,
            List<String> addAdmins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            String groupLinkState,
            String addMemberPermission,
            String editDetailsPermission,
            String avatar,
            Integer expirationTimer
            ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    String getGroupName(String base64GroupId);

    // To get the group Ids in base 64 format, either use the getBaseGroupIds() method, or
    //   the getGroupIds(dummy) form, where dummy represents any string
    List<String> getBase64GroupIds();
    List<String> getGroupIds(String dummy);

    void setGroupBlocked(String base64GroupId, boolean blocked) throws Error.GroupNotFound;

    List<String> getGroupMembers(String dummy);

    long sendGroupMessage(
            String message, List<String> attachments, String base64GroupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String base64GroupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber;

    String updateGroup(
            String base64GroupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    boolean isRegistered(String number);

    List<Boolean> isRegistered(List<String> numbers);

    void updateProfile(
            String name, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    String version();

    String getObjectPath();

    String link() throws Error.Failure;

    String link(String newDeviceName) throws Error.Failure;

    void addDevice(String uri) throws Error.Failure;

    void removeDevice(int deviceId) throws Error.Failure;

    void register(
            String number, boolean voiceVerification
    ) throws Error.Failure, Error.InvalidNumber, SignalControl.Error.RequiresCaptcha;

    void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) throws Error.Failure, Error.InvalidNumber, SignalControl.Error.RequiresCaptcha;

    void verify(String number, String verificationCode) throws Error.Failure, Error.InvalidNumber;

    void verifyWithPin(String number, String verificationCode, String pin) throws Error.Failure, Error.InvalidNumber;

    List<String> listNumbers();

    List<String> listDevices() throws Error.Failure;

    void updateAccount() throws Error.Failure;

    List<String> listIdentity(String number);

    List<String> getContactNumber(final String name) throws Error.Failure;

    void quitGroup(final byte[] groupId) throws Error.GroupNotFound, Error.Failure;

    boolean isContactBlocked(final String number) throws Error.InvalidNumber;

    boolean isGroupBlocked(final byte[] groupId);

    boolean isMember(final byte[] groupId);

    void joinGroup(final String groupLink) throws Error.Failure;

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
