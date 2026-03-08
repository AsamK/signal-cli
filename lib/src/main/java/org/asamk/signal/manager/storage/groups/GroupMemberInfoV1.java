package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.storage.recipients.RecipientId;

public class GroupMemberInfoV1 implements GroupMemberInfo {

    private final RecipientId recipientId;

    public GroupMemberInfoV1(final RecipientId recipientId) {
        this.recipientId = recipientId;
    }

    @Override
    public RecipientId getRecipientId() {
        return this.recipientId;
    }
}
