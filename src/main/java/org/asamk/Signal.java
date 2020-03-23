package org.asamk;

import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

public interface Signal extends DBusInterface {

    void sendMessage(String message, List<String> attachments, String recipient) throws EncapsulatedExceptions, AttachmentInvalidException, IOException, InvalidNumberException;

    void sendMessage(String message, List<String> attachments, List<String> recipients) throws EncapsulatedExceptions, AttachmentInvalidException, IOException, InvalidNumberException;

    void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions, InvalidNumberException;

    void sendGroupMessage(String message, List<String> attachments, byte[] groupId) throws EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, IOException;

    String getContactName(String number) throws InvalidNumberException;

    void setContactName(String number, String name) throws InvalidNumberException;

    void setContactBlocked(String number, boolean blocked) throws InvalidNumberException;

    void setGroupBlocked(byte[] groupId, boolean blocked) throws GroupNotFoundException;

    List<byte[]> getGroupIds();

    String getGroupName(byte[] groupId);

    List<String> getGroupMembers(byte[] groupId);

    byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, InvalidNumberException;

    boolean isRegistered();

    class MessageReceived extends DBusSignal {

        private long timestamp;
        private String sender;
        private byte[] groupId;
        private String message;
        private List<String> attachments;

        public MessageReceived(String objectpath, long timestamp, String sender, byte[] groupId, String message, List<String> attachments) throws DBusException {
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

        private long timestamp;
        private String sender;

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
}
