package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.UntrustedIdentityException;

import java.util.UUID;

public record JsonMessageEnvelope(
        @Deprecated String source,
        String sourceNumber,
        String sourceUuid,
        String sourceName,
        Integer sourceDevice,
        long timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonDataMessage dataMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonStoryMessage storyMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncMessage syncMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonCallMessage callMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReceiptMessage receiptMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonTypingMessage typingMessage
) {

    public static JsonMessageEnvelope from(
            MessageEnvelope envelope, Throwable exception, Manager m
    ) {
        final RecipientAddress sourceAddress;
        final Integer sourceDevice;
        if (envelope.sourceAddress().isPresent()) {
            sourceAddress = envelope.sourceAddress().get();
            sourceDevice = envelope.sourceDevice();
        } else if (exception instanceof UntrustedIdentityException e) {
            sourceAddress = e.getSender();
            sourceDevice = e.getSenderDevice();
        } else {
            sourceAddress = null;
            sourceDevice = null;
        }

        final String source;
        final String sourceNumber;
        final String sourceUuid;
        final String sourceName;
        if (sourceAddress != null) {
            source = sourceAddress.getLegacyIdentifier();
            sourceNumber = sourceAddress.number().orElse(null);
            sourceUuid = sourceAddress.uuid().map(UUID::toString).orElse(null);
            sourceName = m.getContactOrProfileName(RecipientIdentifier.Single.fromAddress(sourceAddress));
        } else {
            source = null;
            sourceNumber = null;
            sourceUuid = null;
            sourceName = null;
        }
        final var timestamp = envelope.timestamp();
        final var receiptMessage = envelope.receipt().map(JsonReceiptMessage::from).orElse(null);
        final var typingMessage = envelope.typing().map(JsonTypingMessage::from).orElse(null);

        final var dataMessage = envelope.data().map(JsonDataMessage::from).orElse(null);
        final var storyMessage = envelope.story().map(JsonStoryMessage::from).orElse(null);
        final var syncMessage = envelope.sync().map(JsonSyncMessage::from).orElse(null);
        final var callMessage = envelope.call().map(JsonCallMessage::from).orElse(null);

        return new JsonMessageEnvelope(source,
                sourceNumber,
                sourceUuid,
                sourceName,
                sourceDevice,
                timestamp,
                dataMessage,
                storyMessage,
                syncMessage,
                callMessage,
                receiptMessage,
                typingMessage);
    }
}
