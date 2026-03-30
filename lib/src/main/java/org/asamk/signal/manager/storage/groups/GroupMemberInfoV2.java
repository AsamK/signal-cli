package org.asamk.signal.manager.storage.groups;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.core.models.ServiceId;
import org.signal.storageservice.storage.protos.groups.Member;
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember;

public class GroupMemberInfoV2 implements GroupMemberInfo {

    private final RecipientResolver recipientResolver;
    private final DecryptedMember member;

    public GroupMemberInfoV2(final DecryptedMember member, final RecipientResolver recipientResolver) {
        this.recipientResolver = recipientResolver;
        this.member = member;
    }

    @Override
    public RecipientId getRecipientId() {
        return recipientResolver.resolveRecipient(ServiceId.ACI.parseOrThrow(member.aciBytes));
    }

    @Override
    public boolean isAdmin() {
        return member.role == Member.Role.ADMINISTRATOR;
    }

    @Override
    public String labelEmoji() {
        return member.labelEmoji.isEmpty() ? null : member.labelEmoji;
    }

    @Override
    public String labelString() {
        return member.labelString.isEmpty() ? null : member.labelString;
    }
}
