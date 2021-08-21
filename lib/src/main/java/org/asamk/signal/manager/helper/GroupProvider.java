package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.groups.GroupInfo;

public interface GroupProvider {

    GroupInfo getGroup(GroupId groupId);
}
