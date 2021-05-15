package org.asamk.signal;

public enum GroupLinkState {
    ENABLED {
        @Override
        public String toString() {
            return "enabled";
        }
    },
    ENABLED_WITH_APPROVAL {
        @Override
        public String toString() {
            return "enabled-with-approval";
        }
    },
    DISABLED {
        @Override
        public String toString() {
            return "disabled";
        }
    };

    public org.asamk.signal.manager.groups.GroupLinkState toLinkState() {
        switch (this) {
            case ENABLED:
                return org.asamk.signal.manager.groups.GroupLinkState.ENABLED;
            case ENABLED_WITH_APPROVAL:
                return org.asamk.signal.manager.groups.GroupLinkState.ENABLED_WITH_APPROVAL;
            case DISABLED:
                return org.asamk.signal.manager.groups.GroupLinkState.DISABLED;
            default:
                throw new AssertionError();
        }
    }
}
