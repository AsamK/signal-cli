package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SendReceiptAction implements HandleAction {

    private final RecipientId recipientId;
    private final List<Long> timestamps = new ArrayList<>();

    public SendReceiptAction(final RecipientId recipientId, final long timestamp) {
        this.recipientId = recipientId;
        this.timestamps.add(timestamp);
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSendHelper().sendDeliveryReceipt(recipientId, timestamps);
    }

    @Override
    public void mergeOther(final HandleAction action) {
        if (action instanceof SendReceiptAction sendReceiptAction) {
            this.timestamps.addAll(sendReceiptAction.timestamps);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SendReceiptAction that = (SendReceiptAction) o;
        // Using only recipientId here on purpose
        return recipientId.equals(that.recipientId);
    }

    @Override
    public int hashCode() {
        // Using only recipientId here on purpose
        return Objects.hash(recipientId);
    }
}
