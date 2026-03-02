package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "Payment")
public record JsonPayment(
        @Schema(required = true) String note,
        @Schema(required = true) byte[] receipt
) {

    static JsonPayment from(MessageEnvelope.Data.Payment payment) {
        return new JsonPayment(payment.note(), payment.receipt());
    }
}
