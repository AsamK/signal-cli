package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.storage.recipients.RecipientId;

public interface GroupMemberInfo {

    RecipientId getRecipientId();

    default boolean isAdmin() {
        return false;
    }

    default String labelEmoji() {
        return null;
    }

    default String labelString() {
        return null;
    }
}
