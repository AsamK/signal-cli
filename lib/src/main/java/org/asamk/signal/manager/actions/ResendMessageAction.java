package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogEntry;

import java.util.Objects;

public class ResendMessageAction implements HandleAction {

    private final RecipientId recipientId;
    private final long timestamp;
    private final MessageSendLogEntry messageSendLogEntry;

    public ResendMessageAction(
            final RecipientId recipientId, final long timestamp, final MessageSendLogEntry messageSendLogEntry
    ) {
        this.recipientId = recipientId;
        this.timestamp = timestamp;
        this.messageSendLogEntry = messageSendLogEntry;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSendHelper().resendMessage(recipientId, timestamp, messageSendLogEntry);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ResendMessageAction that = (ResendMessageAction) o;
        return timestamp == that.timestamp
                && recipientId.equals(that.recipientId)
                && messageSendLogEntry.equals(that.messageSendLogEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientId, timestamp, messageSendLogEntry);
    }
}
