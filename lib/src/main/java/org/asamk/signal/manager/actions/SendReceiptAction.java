package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SendReceiptAction implements HandleAction {

    private final RecipientId recipientId;
    private final SignalServiceReceiptMessage.Type type;
    private final List<Long> timestamps = new ArrayList<>();

    public SendReceiptAction(
            final RecipientId recipientId, final SignalServiceReceiptMessage.Type type, final long timestamp
    ) {
        this.recipientId = recipientId;
        this.type = type;
        this.timestamps.add(timestamp);
    }

    @Override
    public void execute(Context context) throws Throwable {
        final var receiptMessage = new SignalServiceReceiptMessage(type, timestamps, System.currentTimeMillis());

        context.getSendHelper().sendReceiptMessage(receiptMessage, recipientId);
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
        // Using only recipientId and type here on purpose
        return recipientId.equals(that.recipientId) && type == that.type;
    }

    @Override
    public int hashCode() {
        // Using only recipientId and type here on purpose
        return Objects.hash(recipientId, type);
    }
}
