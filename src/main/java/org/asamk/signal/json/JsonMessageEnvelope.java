package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.List;
import java.util.UUID;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonMessageEnvelope {

    @JsonProperty
    @Deprecated
    final String source;

    @JsonProperty
    final String sourceNumber;

    @JsonProperty
    final String sourceUuid;

    @JsonProperty
    final String sourceName;

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

    public JsonMessageEnvelope(
            SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception, Manager m
    ) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            var source = envelope.getSourceAddress();
            this.source = getLegacyIdentifier(source);
            this.sourceNumber = source.getNumber().orNull();
            this.sourceUuid = source.getUuid().transform(UUID::toString).orNull();
            this.sourceDevice = envelope.getSourceDevice();
            this.relay = source.getRelay().orNull();
        } else if (envelope.isUnidentifiedSender() && content != null) {
            final var source = content.getSender();
            this.source = getLegacyIdentifier(source);
            this.sourceNumber = source.getNumber().orNull();
            this.sourceUuid = source.getUuid().transform(UUID::toString).orNull();
            this.sourceDevice = content.getSenderDevice();
            this.relay = null;
        } else if (exception instanceof ProtocolUntrustedIdentityException) {
            var e = (ProtocolUntrustedIdentityException) exception;
            final var source = m.resolveSignalServiceAddress(e.getSender());
            this.source = getLegacyIdentifier(source);
            this.sourceNumber = source.getNumber().orNull();
            this.sourceUuid = source.getUuid().transform(UUID::toString).orNull();
            this.sourceDevice = e.getSenderDevice();
            this.relay = null;
        } else {
            this.source = null;
            this.sourceNumber = null;
            this.sourceUuid = null;
            this.sourceDevice = null;
            this.relay = null;
        }
        String name;
        try {
            name = m.getContactOrProfileName(RecipientIdentifier.Single.fromString(this.source, m.getUsername()));
        } catch (InvalidNumberException | NullPointerException e) {
            name = null;
        }
        this.sourceName = name;
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
        sourceNumber = null;
        sourceUuid = null;
        sourceName = null;
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
        sourceNumber = null;
        sourceUuid = null;
        sourceName = null;
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
        sourceNumber = null;
        sourceUuid = null;
        sourceName = null;
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
