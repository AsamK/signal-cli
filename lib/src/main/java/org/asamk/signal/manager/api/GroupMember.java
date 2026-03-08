package org.asamk.signal.manager.api;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.groups.GroupMemberInfo;

public record GroupMember(
        RecipientAddress recipientAddress, boolean isAdmin, String labelEmoji, String label
) {

    public static GroupMember from(final GroupMemberInfo memberInfo, final RecipientAddressResolver recipientStore) {
        return new GroupMember(recipientStore.resolveRecipientAddress(memberInfo.getRecipientId())
                .toApiRecipientAddress(), memberInfo.isAdmin(), memberInfo.labelEmoji(), memberInfo.labelString());
    }
}
