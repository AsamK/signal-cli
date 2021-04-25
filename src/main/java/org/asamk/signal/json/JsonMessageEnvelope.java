package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
//import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.util.List;

public class JsonMessageEnvelope {
<<<<<<< HEAD
    String source;
    int sourceDevice;
    String relay;
    long timestamp;
    JsonDataMessage dataMessage;
    JsonSyncMessage syncMessage;
    JsonCallMessage callMessage;
    JsonReceiptMessage receiptMessage;
    // String typingAction;
=======

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
>>>>>>> upstream/master

    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent content, Manager m) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            var source = envelope.getSourceAddress();
            this.source = source.getLegacyIdentifier();
            this.sourceDevice = envelope.getSourceDevice();
            this.relay = source.getRelay().orNull();
        } else if (envelope.isUnidentifiedSender() && content != null) {
            this.source = content.getSender().getLegacyIdentifier();
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
<<<<<<< HEAD
        if (content != null) {
            if (envelope.isUnidentifiedSender()) {
                this.source = content.getSender().getLegacyIdentifier();
                this.sourceDevice = content.getSenderDevice();
            }
            if (content.getDataMessage().isPresent()) {
                this.dataMessage = new JsonDataMessage(content.getDataMessage().get(), m);
            }
            if (content.getSyncMessage().isPresent()) {
                this.syncMessage = new JsonSyncMessage(content.getSyncMessage().get(), m);
            }
            if (content.getCallMessage().isPresent()) {
                this.callMessage = new JsonCallMessage(content.getCallMessage().get());
            }
            if (content.getReceiptMessage().isPresent()) {
                this.receiptMessage = new JsonReceiptMessage(content.getReceiptMessage().get());
            }
/*            if (content.getTypingMessage().isPresent()) {
                SignalServiceTypingMessage typingMessage = content.getTypingMessage().get();
                this.typingAction = content.getTypingMessage().get();
           }
*/        }
=======
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
>>>>>>> upstream/master
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
