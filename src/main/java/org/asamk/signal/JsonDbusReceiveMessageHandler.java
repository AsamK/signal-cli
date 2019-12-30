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
                        envelope.getSourceE164().get()
                ));
            } catch (DBusException e) {
                e.printStackTrace();
            }
        } else if (content != null && content.getDataMessage().isPresent()) {
            SignalServiceDataMessage message = content.getDataMessage().get();

            if (!message.isEndSession() &&
                    !(message.getGroupInfo().isPresent() &&
                            message.getGroupInfo().get().getType() != SignalServiceGroup.Type.DELIVER)) {
                List<String> attachments = new ArrayList<>();
                if (message.getAttachments().isPresent()) {
                    for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                        if (attachment.isPointer()) {
                            attachments.add(m.getAttachmentFile(attachment.asPointer().getId()).getAbsolutePath());
                        }
                    }
                }

                try {
                    conn.sendSignal(new Signal.MessageReceived(
                            objectPath,
                            message.getTimestamp(),
                            envelope.isUnidentifiedSender() ? content.getSender().getNumber().get() : envelope.getSourceE164().get(),
                            message.getGroupInfo().isPresent() ? message.getGroupInfo().get().getGroupId() : new byte[0],
                            message.getBody().isPresent() ? message.getBody().get() : "",
                            attachments));
                } catch (DBusException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        super.handleMessage(envelope, content, exception);

        sendReceivedMessageToDbus(envelope, content, conn, objectPath, m);
    }
}
