package org.asamk.signal;

public enum GroupPermission {
    EVERY_MEMBER {
        @Override
        public String toString() {
            return "every-member";
        }
    },
    ONLY_ADMINS {
        @Override
        public String toString() {
            return "only-admins";
        }
    };

    public org.asamk.signal.manager.groups.GroupPermission toManager() {
        switch (this) {
            case EVERY_MEMBER:
                return org.asamk.signal.manager.groups.GroupPermission.EVERY_MEMBER;
            case ONLY_ADMINS:
                return org.asamk.signal.manager.groups.GroupPermission.ONLY_ADMINS;
            default:
                throw new AssertionError();
        }
    }
}
