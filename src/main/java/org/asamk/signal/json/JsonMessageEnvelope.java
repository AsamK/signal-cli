package org.asamk.signal.json;

import org.asamk.Signal;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class JsonMessageEnvelope {

    String source;
    int sourceDevice;
    String relay;
    long timestamp;
    boolean isReceipt;
    JsonDataMessage dataMessage;
    JsonSyncMessage syncMessage;
    JsonCallMessage callMessage;
    JsonReceiptMessage receiptMessage;

    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent content) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            SignalServiceAddress source = envelope.getSourceAddress();
            this.source = source.getLegacyIdentifier();
            this.relay = source.getRelay().isPresent() ? source.getRelay().get() : null;
        }
        this.sourceDevice = envelope.getSourceDevice();
        this.timestamp = envelope.getTimestamp();
        this.isReceipt = envelope.isReceipt();
        if (content != null) {
            if (envelope.isUnidentifiedSender()) {
                this.source = content.getSender().getLegacyIdentifier();
                this.sourceDevice = content.getSenderDevice();
            }
            if (content.getDataMessage().isPresent()) {
                this.dataMessage = new JsonDataMessage(content.getDataMessage().get());
            }
            if (content.getSyncMessage().isPresent()) {
                this.syncMessage = new JsonSyncMessage(content.getSyncMessage().get());
            }
            if (content.getCallMessage().isPresent()) {
                this.callMessage = new JsonCallMessage(content.getCallMessage().get());
            }
            if (content.getReceiptMessage().isPresent()) {
                this.receiptMessage = new JsonReceiptMessage(content.getReceiptMessage().get());
            }
        }
    }

    public JsonMessageEnvelope(Signal.MessageReceived messageReceived) {
        source = messageReceived.getSender();
        timestamp = messageReceived.getTimestamp();
        dataMessage = new JsonDataMessage(messageReceived);
    }

    public JsonMessageEnvelope(Signal.ReceiptReceived receiptReceived) {
        source = receiptReceived.getSender();
        timestamp = receiptReceived.getTimestamp();
        isReceipt = true;
    }

    public JsonMessageEnvelope(Signal.SyncMessageReceived messageReceived) {
        source = messageReceived.getSource();
        timestamp = messageReceived.getTimestamp();
        syncMessage = new JsonSyncMessage(messageReceived);
    }
}
