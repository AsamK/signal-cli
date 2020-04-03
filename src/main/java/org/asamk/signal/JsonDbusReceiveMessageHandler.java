package org.asamk.signal;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

import java.util.ArrayList;
import java.util.List;

public class JsonDbusReceiveMessageHandler extends JsonReceiveMessageHandler {

    private final DBusConnection conn;

    private final String objectPath;

    public JsonDbusReceiveMessageHandler(Manager m, DBusConnection conn, final String objectPath) {
        super(m);
        this.conn = conn;
        this.objectPath = objectPath;
    }

    static void sendReceivedMessageToDbus(SignalServiceEnvelope envelope, SignalServiceContent content, DBusConnection conn, final String objectPath, Manager m) {
        if (envelope.isReceipt()) {
            try {
                conn.sendSignal(new Signal.ReceiptReceived(
                        objectPath,
                        envelope.getTimestamp(),
                        !envelope.isUnidentifiedSender() && envelope.hasSource() ? envelope.getSourceE164().get() : content.getSender().getNumber().get()
                ));
            } catch (DBusException e) {
                e.printStackTrace();
            }
        } else if (content != null) {
            if (content.getReceiptMessage().isPresent()) {
                final SignalServiceReceiptMessage receiptMessage = content.getReceiptMessage().get();
                if (receiptMessage.isDeliveryReceipt()) {
                    final String sender = !envelope.isUnidentifiedSender() && envelope.hasSource() ? envelope.getSourceE164().get() : content.getSender().getNumber().get();
                    for (long timestamp : receiptMessage.getTimestamps()) {
                        try {
                            conn.sendSignal(new Signal.ReceiptReceived(
                                    objectPath,
                                    timestamp,
                                    sender
                            ));
                        } catch (DBusException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();

                if (!message.isEndSession() &&
                        !(message.getGroupContext().isPresent() &&
                                message.getGroupContext().get().getGroupV1Type() != SignalServiceGroup.Type.DELIVER)) {
                    try {
                        conn.sendSignal(new Signal.MessageReceived(
                                objectPath,
                                message.getTimestamp(),
                                envelope.isUnidentifiedSender() || !envelope.hasSource() ? content.getSender().getNumber().get() : envelope.getSourceE164().get(),
                                message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1().isPresent()
                                        ? message.getGroupContext().get().getGroupV1().get().getGroupId() : new byte[0],
                                message.getBody().isPresent() ? message.getBody().get() : "",
                                JsonDbusReceiveMessageHandler.getAttachments(message, m)));
                    } catch (DBusException e) {
                        e.printStackTrace();
                    }
                }
            } else if (content.getSyncMessage().isPresent()) {
                SignalServiceSyncMessage sync_message = content.getSyncMessage().get();
                if (sync_message.getSent().isPresent()) {
                    SentTranscriptMessage transcript = sync_message.getSent().get();

                    if (!envelope.isUnidentifiedSender() && envelope.hasSource() && (transcript.getDestination().isPresent() || transcript.getMessage().getGroupContext().isPresent())) {
                        SignalServiceDataMessage message = transcript.getMessage();

                        try {
                            conn.sendSignal(new Signal.SyncMessageReceived(
                                    objectPath,
                                    transcript.getTimestamp(),
                                    envelope.getSourceAddress().getNumber().get(),
                                    transcript.getDestination().isPresent() ? transcript.getDestination().get().getNumber().get() : "",
                                    message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1().isPresent()
                                            ? message.getGroupContext().get().getGroupV1().get().getGroupId() : new byte[0],
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

    static private List<String> getAttachments(SignalServiceDataMessage message, Manager m) {
        List<String> attachments = new ArrayList<>();
        if (message.getAttachments().isPresent()) {
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                if (attachment.isPointer()) {
                    attachments.add(m.getAttachmentFile(attachment.asPointer().getId()).getAbsolutePath());
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
