package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

@JsonSchema(title = "Payment")
public record JsonPayment(
        String note,
        byte[] receipt
) {

    static JsonPayment from(MessageEnvelope.Data.Payment payment) {
        return new JsonPayment(payment.note(), payment.receipt());
    }
}
