package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

class JsonMessageEnvelope {

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
        SignalServiceAddress source = envelope.getSourceAddress();
        this.source = source.getNumber().get();
        this.sourceDevice = envelope.getSourceDevice();
        this.relay = source.getRelay().isPresent() ? source.getRelay().get() : null;
        this.timestamp = envelope.getTimestamp();
        this.isReceipt = envelope.isReceipt();
        if (content != null) {
            if (envelope.isUnidentifiedSender()) {
                this.source = content.getSender().getNumber().get();
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
}
