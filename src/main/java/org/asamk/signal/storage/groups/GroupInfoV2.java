package org.asamk.signal.storage.groups;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupInfoV2 extends GroupInfo {

    private final GroupMasterKey masterKey;

    private boolean blocked;
    private DecryptedGroup group; // stored as a file with hexadecimal groupId as name

    public GroupInfoV2(final byte[] groupId, final GroupMasterKey masterKey) {
        super(groupId);
        this.masterKey = masterKey;
    }

    public GroupMasterKey getMasterKey() {
        return masterKey;
    }

    public void setGroup(final DecryptedGroup group) {
        this.group = group;
    }

    public DecryptedGroup getGroup() {
        return group;
    }

    @Override
    public String getTitle() {
        if (this.group == null) {
            return null;
        }
        return this.group.getTitle();
    }

    @Override
    public Set<SignalServiceAddress> getMembers() {
        if (this.group == null) {
            return Collections.emptySet();
        }
        return group.getMembersList()
                .stream()
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<SignalServiceAddress> getPendingMembers() {
        if (this.group == null) {
            return Collections.emptySet();
        }
        return group.getPendingMembersList()
                .stream()
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<SignalServiceAddress> getRequestingMembers() {
        if (this.group == null) {
            return Collections.emptySet();
        }
        return group.getRequestingMembersList()
                .stream()
                .map(m -> new SignalServiceAddress(UuidUtil.parseOrThrow(m.getUuid().toByteArray()), null))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isBlocked() {
        return blocked;
    }

    @Override
    public void setBlocked(final boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public int getMessageExpirationTime() {
        return this.group != null && this.group.hasDisappearingMessagesTimer()
                ? this.group.getDisappearingMessagesTimer().getDuration()
                : 0;
    }
}
