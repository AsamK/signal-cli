package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.Profile;
import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileUtilsTest {

    @Test
    void badgesAreConvertedFromServiceProfile() {
        final var profile = profileWithBadges(List.of(badge("BOOST", "donor", new BigDecimal("1767225600"), true),
                badge("TEST", "other", null, false)));

        assertEquals(List.of(new Profile.Badge("BOOST", "donor", 1767225600000L, true),
                new Profile.Badge("TEST", "other", null, false)), ProfileUtils.getBadges(profile));
    }

    @Test
    void missingBadgesResultInEmptyList() {
        assertEquals(List.of(), ProfileUtils.getBadges(profileWithBadges(null)));
    }

    private static SignalServiceProfile.Badge badge(
            final String id,
            final String category,
            final BigDecimal expiration,
            final boolean visible
    ) {
        return new SignalServiceProfile.Badge(id, category, "", "", List.of(), expiration, visible, 0);
    }

    private static SignalServiceProfile profileWithBadges(final List<SignalServiceProfile.Badge> badges) {
        return new SignalServiceProfile(null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                badges,
                null);
    }
}
