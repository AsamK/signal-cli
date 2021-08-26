package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public class RetrieveProfileAction implements HandleAction {

    private final RecipientId recipientId;

    public RetrieveProfileAction(final RecipientId recipientId) {
        this.recipientId = recipientId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getProfileHelper().refreshRecipientProfile(recipientId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RetrieveProfileAction that = (RetrieveProfileAction) o;

        return recipientId.equals(that.recipientId);
    }

    @Override
    public int hashCode() {
        return recipientId.hashCode();
    }
}
