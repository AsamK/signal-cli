package org.asamk.signal.manager.storage.profiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public class SignalProfile {

    @JsonProperty
    private final String identityKey;

    @JsonProperty
    private final String name;

    @JsonProperty
    private final String about;

    @JsonProperty
    private final String aboutEmoji;

    @JsonProperty
    private final String unidentifiedAccess;

    @JsonProperty
    private final boolean unrestrictedUnidentifiedAccess;

    @JsonProperty
    private final Capabilities capabilities;

    public SignalProfile(
            final String identityKey,
            final String name,
            final String about,
            final String aboutEmoji,
            final String unidentifiedAccess,
            final boolean unrestrictedUnidentifiedAccess,
            final SignalServiceProfile.Capabilities capabilities
    ) {
        this.identityKey = identityKey;
        this.name = name;
        this.about = about;
        this.aboutEmoji = aboutEmoji;
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
            @JsonProperty("about") final String about,
            @JsonProperty("aboutEmoji") final String aboutEmoji,
            @JsonProperty("unidentifiedAccess") final String unidentifiedAccess,
            @JsonProperty("unrestrictedUnidentifiedAccess") final boolean unrestrictedUnidentifiedAccess,
            @JsonProperty("capabilities") final Capabilities capabilities
    ) {
        this.identityKey = identityKey;
        this.name = name;
        this.about = about;
        this.aboutEmoji = aboutEmoji;
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

    public String getDisplayName() {
        // First name and last name (if set) are separated by a NULL char + trim space in case only one is filled
        return name == null ? null : name.replace("\0", " ").trim();
    }

    public String getAbout() {
        return about;
    }

    public String getAboutEmoji() {
        return aboutEmoji;
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
                + ", about='"
                + about
                + '\''
                + ", aboutEmoji='"
                + aboutEmoji
                + '\''
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
