package org.asamk.signal.manager.storage.profiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.io.File;

public class SignalProfile {

    @JsonProperty
    private final String identityKey;

    @JsonProperty
    private final String name;

    private final File avatarFile;

    @JsonProperty
    private final String unidentifiedAccess;

    @JsonProperty
    private final boolean unrestrictedUnidentifiedAccess;

    @JsonProperty
    private final Capabilities capabilities;

    public SignalProfile(
            final String identityKey,
            final String name,
            final File avatarFile,
            final String unidentifiedAccess,
            final boolean unrestrictedUnidentifiedAccess,
            final SignalServiceProfile.Capabilities capabilities
    ) {
        this.identityKey = identityKey;
        this.name = name;
        this.avatarFile = avatarFile;
        this.unidentifiedAccess = unidentifiedAccess;
        this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
        this.capabilities = new Capabilities();
        this.capabilities.storage = capabilities.isStorage();
        this.capabilities.gv1Migration = capabilities.isGv1Migration();
        this.capabilities.gv2 = capabilities.isGv2();
    }

    public SignalProfile(
            @JsonProperty("identityKey") final String identityKey,
            @JsonProperty("name") final String name,
            @JsonProperty("unidentifiedAccess") final String unidentifiedAccess,
            @JsonProperty("unrestrictedUnidentifiedAccess") final boolean unrestrictedUnidentifiedAccess,
            @JsonProperty("capabilities") final Capabilities capabilities
    ) {
        this.identityKey = identityKey;
        this.name = name;
        this.avatarFile = null;
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

    public Capabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public String toString() {
        return "SignalProfile{"
                + "identityKey='"
                + identityKey
                + '\''
                + ", name='"
                + name
                + '\''
                + ", avatarFile="
                + avatarFile
                + ", unidentifiedAccess='"
                + unidentifiedAccess
                + '\''
                + ", unrestrictedUnidentifiedAccess="
                + unrestrictedUnidentifiedAccess
                + ", capabilities="
                + capabilities
                + '}';
    }

    public static class Capabilities {

        @JsonIgnore
        public boolean uuid;

        @JsonProperty
        public boolean gv2;

        @JsonProperty
        public boolean storage;

        @JsonProperty
        public boolean gv1Migration;
    }
}
