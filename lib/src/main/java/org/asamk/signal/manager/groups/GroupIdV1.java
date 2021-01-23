package org.asamk.signal.manager.groups;

import static org.asamk.signal.manager.util.KeyUtils.getSecretBytes;

public class GroupIdV1 extends GroupId {

    public static GroupIdV1 createRandom() {
        return new GroupIdV1(getSecretBytes(16));
    }

    public GroupIdV1(final byte[] id) {
        super(id);
    }
}
