package org.asamk.signal.manager;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.io.File;

public class SignalProfile {

    private final String identityKey;

    private final String name;

    private final File avatarFile;

    private final String unidentifiedAccess;

    private final boolean unrestrictedUnidentifiedAccess;

    private final SignalServiceProfile.Capabilities capabilities;

    public SignalProfile(final String identityKey, final String name, final File avatarFile, final String unidentifiedAccess, final boolean unrestrictedUnidentifiedAccess, final SignalServiceProfile.Capabilities capabilities) {
        this.identityKey = identityKey;
        this.name = name;
        this.avatarFile = avatarFile;
        this.unidentifiedAccess = unidentifiedAccess;
        this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
        this.capabilities = capabilities;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public String getName() {
        return name;
    }

    public File getAvatarFile() {
        return avatarFile;
    }

    public String getUnidentifiedAccess() {
        return unidentifiedAccess;
    }

    public boolean isUnrestrictedUnidentifiedAccess() {
        return unrestrictedUnidentifiedAccess;
    }

    public SignalServiceProfile.Capabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return "SignalProfile{" +
                "identityKey='" + identityKey + '\'' +
                ", name='" + name + '\'' +
                ", avatarFile=" + avatarFile +
                ", unidentifiedAccess='" + unidentifiedAccess + '\'' +
                ", unrestrictedUnidentifiedAccess=" + unrestrictedUnidentifiedAccess +
                ", capabilities=" + capabilities +
                '}';
    }
}
