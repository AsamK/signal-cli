package org.asamk;

import org.asamk.textsecure.AttachmentInvalidException;
import org.asamk.textsecure.GroupNotFoundException;
import org.freedesktop.dbus.DBusInterface;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;

import java.io.IOException;
import java.util.List;

public interface TextSecure extends DBusInterface {
    void sendMessage(String message, List<String> attachments, String recipient) throws EncapsulatedExceptions, AttachmentInvalidException, IOException;

    void sendMessage(String message, List<String> attachments, List<String> recipients) throws EncapsulatedExceptions, AttachmentInvalidException, IOException;

    void sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions;

    void sendGroupMessage(String message, List<String> attachments, byte[] groupId) throws EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, IOException;
}
