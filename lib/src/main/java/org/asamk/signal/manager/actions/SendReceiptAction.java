package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.List;
import java.util.Objects;

public class SendReceiptAction implements HandleAction {

    private final RecipientId recipientId;
    private final long timestamp;

    public SendReceiptAction(final RecipientId recipientId, final long timestamp) {
        this.recipientId = recipientId;
        this.timestamp = timestamp;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSendHelper().sendDeliveryReceipt(recipientId, List.of(timestamp));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final var that = (SendReceiptAction) o;
        return timestamp == that.timestamp && recipientId.equals(that.recipientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientId, timestamp);
    }
}
