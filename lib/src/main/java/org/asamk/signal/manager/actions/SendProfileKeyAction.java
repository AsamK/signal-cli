package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.Objects;

public class SendProfileKeyAction implements HandleAction {

    private final RecipientId recipientId;

    public SendProfileKeyAction(final RecipientId recipientId) {
        this.recipientId = recipientId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSendHelper().sendProfileKey(recipientId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SendProfileKeyAction that = (SendProfileKeyAction) o;
        return recipientId.equals(that.recipientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipientId);
    }
}
