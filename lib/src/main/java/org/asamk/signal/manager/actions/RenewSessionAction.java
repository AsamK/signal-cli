package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public class RenewSessionAction implements HandleAction {

    private final RecipientId recipientId;

    public RenewSessionAction(final RecipientId recipientId) {
        this.recipientId = recipientId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccount().getSessionStore().archiveSessions(recipientId);
        if (!recipientId.equals(context.getAccount().getSelfRecipientId())) {
            context.getSendHelper().sendNullMessage(recipientId);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RenewSessionAction that = (RenewSessionAction) o;

        return recipientId.equals(that.recipientId);
    }

    @Override
    public int hashCode() {
        return recipientId.hashCode();
    }
}
