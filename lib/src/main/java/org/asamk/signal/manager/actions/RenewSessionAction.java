package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ServiceId;

public class RenewSessionAction implements HandleAction {

    private final RecipientId recipientId;
    private final ServiceId serviceId;
    private final ServiceId accountId;

    public RenewSessionAction(final RecipientId recipientId, final ServiceId serviceId, final ServiceId accountId) {
        this.recipientId = recipientId;
        this.serviceId = serviceId;
        this.accountId = accountId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccount().getAccountData(accountId).getSessionStore().archiveSessions(serviceId);
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
