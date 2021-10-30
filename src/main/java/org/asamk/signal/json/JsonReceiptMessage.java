package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

import java.util.List;

record JsonReceiptMessage(long when, boolean isDelivery, boolean isRead, boolean isViewed, List<Long> timestamps) {

    static JsonReceiptMessage from(SignalServiceReceiptMessage receiptMessage) {
        final var when = receiptMessage.getWhen();
        final var isDelivery = receiptMessage.isDeliveryReceipt();
        final var isRead = receiptMessage.isReadReceipt();
        final var isViewed = receiptMessage.isViewedReceipt();
        final var timestamps = receiptMessage.getTimestamps();
        return new JsonReceiptMessage(when, isDelivery, isRead, isViewed, timestamps);
    }

    static JsonReceiptMessage deliveryReceipt(final long when, final List<Long> timestamps) {
        return new JsonReceiptMessage(when, true, false, false, timestamps);
    }
}
