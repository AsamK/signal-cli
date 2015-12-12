package cli;

import org.freedesktop.dbus.DBusInterface;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;

import java.io.IOException;
import java.util.List;

public interface TextSecure extends DBusInterface {
    void sendMessage(String message, List<String> attachments, String recipient) throws EncapsulatedExceptions, AttachmentInvalidException, IOException;

    void sendGroupMessage(String message, List<String> attachments, byte[] groupId) throws EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, IOException;
}
