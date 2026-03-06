package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

@JsonSchema(title = "ReceiptMessage")
record JsonReceiptMessage(
        @JsonProperty(required = true) long when,
        @JsonProperty(required = true) boolean isDelivery,
        @JsonProperty(required = true) boolean isRead,
        @JsonProperty(required = true) boolean isViewed,
        @JsonProperty(required = true) List<Long> timestamps
) {

    static JsonReceiptMessage from(MessageEnvelope.Receipt receiptMessage) {
        final var when = receiptMessage.when();
        final var isDelivery = receiptMessage.type() == MessageEnvelope.Receipt.Type.DELIVERY;
        final var isRead = receiptMessage.type() == MessageEnvelope.Receipt.Type.READ;
        final var isViewed = receiptMessage.type() == MessageEnvelope.Receipt.Type.VIEWED;
        final var timestamps = receiptMessage.timestamps();
        return new JsonReceiptMessage(when, isDelivery, isRead, isViewed, timestamps);
    }
}
