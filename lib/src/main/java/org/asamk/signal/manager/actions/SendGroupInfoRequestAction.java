package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.groups.GroupIdV1;
import org.asamk.signal.manager.jobs.Context;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public class SendGroupInfoRequestAction implements HandleAction {

    private final RecipientId recipientId;
    private final GroupIdV1 groupId;

    public SendGroupInfoRequestAction(final RecipientId recipientId, final GroupIdV1 groupId) {
        this.recipientId = recipientId;
        this.groupId = groupId;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getGroupHelper().sendGroupInfoRequest(groupId, recipientId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final var that = (SendGroupInfoRequestAction) o;

        if (!recipientId.equals(that.recipientId)) return false;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        var result = recipientId.hashCode();
        result = 31 * result + groupId.hashCode();
        return result;
    }
}
