package org.asamk.signal.manager.groups;

import java.util.Base64;

public class GroupIdV2 extends GroupId {

    public static GroupIdV2 fromBase64(String groupId) {
        return new GroupIdV2(Base64.getDecoder().decode(groupId));
    }

    public GroupIdV2(final byte[] id) {
        super(id);
    }
}
