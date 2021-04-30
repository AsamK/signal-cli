package org.asamk.signal.manager.storage.profiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SignalProfile {

    @JsonProperty
    @JsonIgnore
    private String identityKey;

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
            @JsonProperty("name") final String name,
            @JsonProperty("about") final String about,
            @JsonProperty("aboutEmoji") final String aboutEmoji,
            @JsonProperty("unidentifiedAccess") final String unidentifiedAccess,
            @JsonProperty("unrestrictedUnidentifiedAccess") final boolean unrestrictedUnidentifiedAccess,
            @JsonProperty("capabilities") final Capabilities capabilities
    ) {
        this.name = name;
        this.about = about;
        this.aboutEmoji = aboutEmoji;
        this.unidentifiedAccess = unidentifiedAccess;
        this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
        this.capabilities = capabilities;
    }

    public String getGivenName() {
        if (name == null) {
            return null;
        }

        String[] parts = name.split("\0");

        return parts.length < 1 ? null : parts[0];
    }

    public String getFamilyName() {
        if (name == null) {
            return null;
        }

        String[] parts = name.split("\0");

        return parts.length < 2 ? null : parts[1];
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
