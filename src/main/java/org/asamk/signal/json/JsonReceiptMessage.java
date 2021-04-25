package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

import java.util.List;

class JsonReceiptMessage {

    @JsonProperty
    final long when;

    @JsonProperty
    final boolean isDelivery;

    @JsonProperty
    final boolean isRead;

    @JsonProperty
    final List<Long> timestamps;

    JsonReceiptMessage(SignalServiceReceiptMessage receiptMessage) {
        this.when = receiptMessage.getWhen();
        this.isDelivery = receiptMessage.isDeliveryReceipt();
        this.isRead = receiptMessage.isReadReceipt();
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
