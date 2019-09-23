package org.asamk.signal;

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
}
