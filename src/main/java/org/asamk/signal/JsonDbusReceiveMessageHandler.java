package org.asamk.signal;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.ArrayList;
import java.util.List;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonDbusReceiveMessageHandler extends JsonReceiveMessageHandler {

    private final DBusConnection conn;

    private final String objectPath;

    public JsonDbusReceiveMessageHandler(Manager m, DBusConnection conn, final String objectPath) {
        super(m);
        this.conn = conn;
        this.objectPath = objectPath;
    }

    static void sendReceivedMessageToDbus(
            SignalServiceEnvelope envelope,
            SignalServiceContent content,
            DBusConnection conn,
            final String objectPath,
            Manager m
    ) {
        if (envelope.isReceipt()) {
            try {
                conn.sendMessage(new Signal.ReceiptReceived(objectPath, envelope.getTimestamp(),
                        // A receipt envelope always has a source address
                        getLegacyIdentifier(envelope.getSourceAddress())));
            } catch (DBusException e) {
                e.printStackTrace();
            }
        } else if (content != null) {
            final var sender = !envelope.isUnidentifiedSender() && envelope.hasSource()
                    ? envelope.getSourceAddress()
                    : content.getSender();
            if (content.getReceiptMessage().isPresent()) {
                final var receiptMessage = content.getReceiptMessage().get();
                if (receiptMessage.isDeliveryReceipt()) {
                    for (long timestamp : receiptMessage.getTimestamps()) {
                        try {
                            conn.sendMessage(new Signal.ReceiptReceived(objectPath,
                                    timestamp,
                                    getLegacyIdentifier(sender)));
                        } catch (DBusException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else if (content.getDataMessage().isPresent()) {
                var message = content.getDataMessage().get();

                var groupId = getGroupId(message);
                if (!message.isEndSession() && (
                        groupId == null
                                || message.getGroupContext().get().getGroupV1Type() == null
                                || message.getGroupContext().get().getGroupV1Type() == SignalServiceGroup.Type.DELIVER
                )) {
                    try {
                        conn.sendMessage(new Signal.MessageReceived(objectPath,
                                message.getTimestamp(),
                                getLegacyIdentifier(sender),
                                groupId != null ? groupId : new byte[0],
                                message.getBody().isPresent() ? message.getBody().get() : "",
                                JsonDbusReceiveMessageHandler.getAttachments(message, m)));
                    } catch (DBusException e) {
                        e.printStackTrace();
                    }
                }
            } else if (content.getSyncMessage().isPresent()) {
                var sync_message = content.getSyncMessage().get();
                if (sync_message.getSent().isPresent()) {
                    var transcript = sync_message.getSent().get();

                    if (transcript.getDestination().isPresent() || transcript.getMessage()
                            .getGroupContext()
                            .isPresent()) {
                        var message = transcript.getMessage();
                        var groupId = getGroupId(message);

                        try {
                            conn.sendMessage(new Signal.SyncMessageReceived(objectPath,
                                    transcript.getTimestamp(),
                                    getLegacyIdentifier(sender),
                                    transcript.getDestination().isPresent()
                                            ? getLegacyIdentifier(transcript.getDestination().get())
                                            : "",
                                    groupId != null ? groupId : new byte[0],
                                    message.getBody().isPresent() ? message.getBody().get() : "",
                                    JsonDbusReceiveMessageHandler.getAttachments(message, m)));
                        } catch (DBusException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static byte[] getGroupId(final SignalServiceDataMessage message) {
        return message.getGroupContext().isPresent() ? GroupUtils.getGroupId(message.getGroupContext().get())
                .serialize() : null;
    }

    static private List<String> getAttachments(SignalServiceDataMessage message, Manager m) {
        var attachments = new ArrayList<String>();
        if (message.getAttachments().isPresent()) {
            for (var attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    attachments.add(m.getAttachmentFile(attachment.asPointer().getRemoteId()).getAbsolutePath());
                }
            }
        }
        return attachments;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        super.handleMessage(envelope, content, exception);

        sendReceivedMessageToDbus(envelope, content, conn, objectPath, m);
    }
}
