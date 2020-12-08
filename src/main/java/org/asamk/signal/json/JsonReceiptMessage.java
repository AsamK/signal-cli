package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

import java.util.List;

class JsonReceiptMessage {

    long when;
    boolean isDelivery;
    boolean isRead;
    List<Long> timestamps;

    JsonReceiptMessage(SignalServiceReceiptMessage receiptMessage) {

        this.when = receiptMessage.getWhen();
        if (receiptMessage.isDeliveryReceipt()) {
            this.isDelivery = true;
        }
        if (receiptMessage.isReadReceipt()) {
            this.isRead = true;
        }
        this.timestamps = receiptMessage.getTimestamps();
    }

    private JsonReceiptMessage(
            final long when, final boolean isDelivery, final boolean isRead, final List<Long> timestamps
    ) {
        this.when = when;
        this.isDelivery = isDelivery;
        this.isRead = isRead;
        this.timestamps = timestamps;
    }

    static JsonReceiptMessage deliveryReceipt(final long when, final List<Long> timestamps) {
        return new JsonReceiptMessage(when, true, false, timestamps);
    }
}
