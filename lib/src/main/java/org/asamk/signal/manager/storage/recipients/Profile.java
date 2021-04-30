package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.internal.util.Util;

import java.util.Collections;
import java.util.Set;

public class Profile {

    private final long lastUpdateTimestamp;

    private final String givenName;

    private final String familyName;

    private final String about;

    private final String aboutEmoji;

    private final UnidentifiedAccessMode unidentifiedAccessMode;

    private final Set<Capability> capabilities;

    public Profile(
            final long lastUpdateTimestamp,
            final String givenName,
            final String familyName,
            final String about,
            final String aboutEmoji,
            final UnidentifiedAccessMode unidentifiedAccessMode,
            final Set<Capability> capabilities
    ) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.givenName = givenName;
        this.familyName = familyName;
        this.about = about;
        this.aboutEmoji = aboutEmoji;
        this.unidentifiedAccessMode = unidentifiedAccessMode;
        this.capabilities = capabilities;
    }

    private Profile(final Builder builder) {
        lastUpdateTimestamp = builder.lastUpdateTimestamp;
        givenName = builder.givenName;
        familyName = builder.familyName;
        about = builder.about;
        aboutEmoji = builder.aboutEmoji;
        unidentifiedAccessMode = builder.unidentifiedAccessMode;
        capabilities = builder.capabilities;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Profile copy) {
        Builder builder = new Builder();
        builder.lastUpdateTimestamp = copy.getLastUpdateTimestamp();
        builder.givenName = copy.getGivenName();
        builder.familyName = copy.getFamilyName();
        builder.about = copy.getAbout();
        builder.aboutEmoji = copy.getAboutEmoji();
        builder.unidentifiedAccessMode = copy.getUnidentifiedAccessMode();
        builder.capabilities = copy.getCapabilities();
        return builder;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getInternalServiceName() {
        if (familyName == null) {
            return givenName == null ? "" : givenName;
        }
        return String.join("\0", givenName == null ? "" : givenName, familyName);
    }

    public String getDisplayName() {
        final var noGivenName = Util.isEmpty(givenName);
        final var noFamilyName = Util.isEmpty(familyName);

        if (noGivenName && noFamilyName) {
            return "";
        } else if (noGivenName) {
            return familyName;
        } else if (noFamilyName) {
            return givenName;
        }

        return givenName + " " + familyName;
    }

    public String getAbout() {
        return about;
    }

    public String getAboutEmoji() {
        return aboutEmoji;
    }

    public UnidentifiedAccessMode getUnidentifiedAccessMode() {
        return unidentifiedAccessMode;
    }

    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    public enum UnidentifiedAccessMode {
        UNKNOWN,
        DISABLED,
        ENABLED,
        UNRESTRICTED;

        static UnidentifiedAccessMode valueOfOrUnknown(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
    }

    public enum Capability {
        gv2,
        storage,
        gv1Migration;

        static Capability valueOfOrNull(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static final class Builder {

        private String givenName;
        private String familyName;
        private String about;
        private String aboutEmoji;
        private UnidentifiedAccessMode unidentifiedAccessMode = UnidentifiedAccessMode.UNKNOWN;
        private Set<Capability> capabilities = Collections.emptySet();
        private long lastUpdateTimestamp = 0;

        private Builder() {
        }

        public Builder withGivenName(final String val) {
            givenName = val;
            return this;
        }

        public Builder withFamilyName(final String val) {
            familyName = val;
            return this;
        }

        public Builder withAbout(final String val) {
            about = val;
            return this;
        }

        public Builder withAboutEmoji(final String val) {
            aboutEmoji = val;
            return this;
        }

        public Builder withUnidentifiedAccessMode(final UnidentifiedAccessMode val) {
            unidentifiedAccessMode = val;
            return this;
        }

        public Builder withCapabilities(final Set<Capability> val) {
            capabilities = val;
            return this;
        }

        public Profile build() {
            return new Profile(this);
        }

        public Builder withLastUpdateTimestamp(final long val) {
            lastUpdateTimestamp = val;
            return this;
        }
    }
}
