package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.util.List;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonMessageEnvelope {

    @JsonProperty
    final String source;

    @JsonProperty
    final Integer sourceDevice;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final String relay;

    @JsonProperty
    final long timestamp;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonDataMessage dataMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonSyncMessage syncMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonCallMessage callMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonReceiptMessage receiptMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonTypingMessage typingMessage;

    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent content, Manager m) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            var source = envelope.getSourceAddress();
            this.source = getLegacyIdentifier(source);
            this.sourceDevice = envelope.getSourceDevice();
            this.relay = source.getRelay().orNull();
        } else if (envelope.isUnidentifiedSender() && content != null) {
            this.source = getLegacyIdentifier(content.getSender());
            this.sourceDevice = content.getSenderDevice();
            this.relay = null;
        } else {
            this.source = null;
            this.sourceDevice = null;
            this.relay = null;
        }
        this.timestamp = envelope.getTimestamp();
        if (envelope.isReceipt()) {
            this.receiptMessage = JsonReceiptMessage.deliveryReceipt(timestamp, List.of(timestamp));
        } else if (content != null && content.getReceiptMessage().isPresent()) {
            this.receiptMessage = new JsonReceiptMessage(content.getReceiptMessage().get());
        } else {
            this.receiptMessage = null;
        }
        this.typingMessage = content != null && content.getTypingMessage().isPresent()
                ? new JsonTypingMessage(content.getTypingMessage().get())
                : null;

        this.dataMessage = content != null && content.getDataMessage().isPresent()
                ? new JsonDataMessage(content.getDataMessage().get(), m)
                : null;
        this.syncMessage = content != null && content.getSyncMessage().isPresent()
                ? new JsonSyncMessage(content.getSyncMessage().get(), m)
                : null;
        this.callMessage = content != null && content.getCallMessage().isPresent()
                ? new JsonCallMessage(content.getCallMessage().get())
                : null;
    }

    public JsonMessageEnvelope(Signal.MessageReceived messageReceived) {
        source = messageReceived.getSender();
        sourceDevice = null;
        relay = null;
        timestamp = messageReceived.getTimestamp();
        receiptMessage = null;
        dataMessage = new JsonDataMessage(messageReceived);
        syncMessage = null;
        callMessage = null;
        typingMessage = null;
    }

    public JsonMessageEnvelope(Signal.ReceiptReceived receiptReceived) {
        source = receiptReceived.getSender();
        sourceDevice = null;
        relay = null;
        timestamp = receiptReceived.getTimestamp();
        receiptMessage = JsonReceiptMessage.deliveryReceipt(timestamp, List.of(timestamp));
        dataMessage = null;
        syncMessage = null;
        callMessage = null;
        typingMessage = null;
    }

    public JsonMessageEnvelope(Signal.SyncMessageReceived messageReceived) {
        source = messageReceived.getSource();
        sourceDevice = null;
        relay = null;
        timestamp = messageReceived.getTimestamp();
        receiptMessage = null;
        dataMessage = null;
        syncMessage = new JsonSyncMessage(messageReceived);
        callMessage = null;
        typingMessage = null;
    }
}
