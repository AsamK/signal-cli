package org.asamk.signal.manager.groups;

import java.util.Base64;

import static org.asamk.signal.manager.util.KeyUtils.getSecretBytes;

public class GroupIdV1 extends GroupId {

    public static GroupIdV1 createRandom() {
        return new GroupIdV1(getSecretBytes(16));
    }

    public static GroupIdV1 fromBase64(String groupId) {
        return new GroupIdV1(Base64.getDecoder().decode(groupId));
    }

    public GroupIdV1(final byte[] id) {
        super(id);
    }
}
