package org.asamk.signal.manager;

public class SignalProfile {

    private final String identityKey;

    private final String name;

    private final String avatar;

    private final String unidentifiedAccess;

    private final boolean unrestrictedUnidentifiedAccess;

    public SignalProfile(final String identityKey, final String name, final String avatar, final String unidentifiedAccess, final boolean unrestrictedUnidentifiedAccess) {
        this.identityKey = identityKey;
        this.name = name;
        this.avatar = avatar;
        this.unidentifiedAccess = unidentifiedAccess;
        this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public String getName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getUnidentifiedAccess() {
        return unidentifiedAccess;
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        return unrestrictedUnidentifiedAccess;
    }

    @Override
    public String toString() {
        return "SignalProfile{" +
                "identityKey='" + identityKey + '\'' +
                ", name='" + name + '\'' +
                ", avatar='" + avatar + '\'' +
                ", unidentifiedAccess='" + unidentifiedAccess + '\'' +
                ", unrestrictedUnidentifiedAccess=" + unrestrictedUnidentifiedAccess +
                '}';
    }
}
