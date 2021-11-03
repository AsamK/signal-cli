package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

record JsonReceiptMessage(long when, boolean isDelivery, boolean isRead, boolean isViewed, List<Long> timestamps) {

    static JsonReceiptMessage from(MessageEnvelope.Receipt receiptMessage) {
        final var when = receiptMessage.when();
        final var isDelivery = receiptMessage.type() == MessageEnvelope.Receipt.Type.DELIVERY;
        final var isRead = receiptMessage.type() == MessageEnvelope.Receipt.Type.READ;
        final var isViewed = receiptMessage.type() == MessageEnvelope.Receipt.Type.VIEWED;
        final var timestamps = receiptMessage.timestamps();
        return new JsonReceiptMessage(when, isDelivery, isRead, isViewed, timestamps);
    }

    static JsonReceiptMessage deliveryReceipt(final long when, final List<Long> timestamps) {
        return new JsonReceiptMessage(when, true, false, false, timestamps);
    }
}
