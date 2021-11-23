package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

public record JsonPayment(String note, byte[] receipt) {
    static JsonPayment from(MessageEnvelope.Data.Payment payment) {
        return new JsonPayment(payment.note(), payment.receipt());
    }
}
