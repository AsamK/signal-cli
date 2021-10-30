package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.List;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public record JsonMessageEnvelope(
        @Deprecated String source,
        String sourceNumber,
        String sourceUuid,
        String sourceName,
        Integer sourceDevice,
        long timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonDataMessage dataMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncMessage syncMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonCallMessage callMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReceiptMessage receiptMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonTypingMessage typingMessage
) {

    public static JsonMessageEnvelope from(
            SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception, Manager m
    ) {
        final String source;
        final String sourceNumber;
        final String sourceUuid;
        final Integer sourceDevice;
        if (!envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) {
            final var sourceAddress = m.resolveSignalServiceAddress(envelope.getSourceAddress());
            source = getLegacyIdentifier(sourceAddress);
            sourceNumber = sourceAddress.getNumber().orNull();
            sourceUuid = sourceAddress.getUuid().toString();
            sourceDevice = envelope.getSourceDevice();
        } else if (envelope.isUnidentifiedSender() && content != null) {
            final var sender = m.resolveSignalServiceAddress(content.getSender());
            source = getLegacyIdentifier(sender);
            sourceNumber = sender.getNumber().orNull();
            sourceUuid = sender.getUuid().toString();
            sourceDevice = content.getSenderDevice();
        } else if (exception instanceof UntrustedIdentityException e) {
            final var sender = m.resolveSignalServiceAddress(e.getSender());
            source = getLegacyIdentifier(sender);
            sourceNumber = sender.getNumber().orNull();
            sourceUuid = sender.getUuid().toString();
            sourceDevice = e.getSenderDevice();
        } else {
            source = null;
            sourceNumber = null;
            sourceUuid = null;
            sourceDevice = null;
        }
        String name;
        try {
            name = m.getContactOrProfileName(RecipientIdentifier.Single.fromString(source, m.getSelfNumber()));
        } catch (InvalidNumberException | NullPointerException e) {
            name = null;
        }
        final var sourceName = name;
        final var timestamp = envelope.getTimestamp();
        final JsonReceiptMessage receiptMessage;
        if (envelope.isReceipt()) {
            receiptMessage = JsonReceiptMessage.deliveryReceipt(timestamp, List.of(timestamp));
        } else if (content != null && content.getReceiptMessage().isPresent()) {
            receiptMessage = JsonReceiptMessage.from(content.getReceiptMessage().get());
        } else {
            receiptMessage = null;
        }
        final var typingMessage = content != null && content.getTypingMessage().isPresent() ? JsonTypingMessage.from(
                content.getTypingMessage().get()) : null;

        final var dataMessage = content != null && content.getDataMessage().isPresent()
                ? JsonDataMessage.from(content.getDataMessage().get(), m)
                : null;
        final var syncMessage = content != null && content.getSyncMessage().isPresent()
                ? JsonSyncMessage.from(content.getSyncMessage().get(), m)
                : null;
        final var callMessage = content != null && content.getCallMessage().isPresent()
                ? JsonCallMessage.from(content.getCallMessage().get())
                : null;

        return new JsonMessageEnvelope(source,
                sourceNumber,
                sourceUuid,
                sourceName,
                sourceDevice,
                timestamp,
                dataMessage,
                syncMessage,
                callMessage,
                receiptMessage,
                typingMessage);
    }

    public static JsonMessageEnvelope from(Signal.MessageReceived messageReceived) {
        return new JsonMessageEnvelope(messageReceived.getSource(),
                null,
                null,
                null,
                null,
                messageReceived.getTimestamp(),
                JsonDataMessage.from(messageReceived),
                null,
                null,
                null,
                null);
    }

    public static JsonMessageEnvelope from(Signal.ReceiptReceived receiptReceived) {
        return new JsonMessageEnvelope(receiptReceived.getSender(),
                null,
                null,
                null,
                null,
                receiptReceived.getTimestamp(),
                null,
                null,
                null,
                JsonReceiptMessage.deliveryReceipt(receiptReceived.getTimestamp(),
                        List.of(receiptReceived.getTimestamp())),
                null);
    }

    public static JsonMessageEnvelope from(Signal.SyncMessageReceived messageReceived) {
        return new JsonMessageEnvelope(messageReceived.getSource(),
                null,
                null,
                null,
                null,
                messageReceived.getTimestamp(),
                null,
                JsonSyncMessage.from(messageReceived),
                null,
                null,
                null);
    }
}
