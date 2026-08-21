package org.asamk.signal.manager.storage.recipients;

import org.asamk.signal.manager.api.Contact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipientStoreTest {

    @Test
    void mergeContactsUsesAndroidMergePolicy() {
        final var primary = Contact.newBuilder()
                .withGivenName("Primary given")
                .withFamilyName("Primary family")
                .withNickName("Primary system nickname")
                .withNickNameGivenName("Primary nickname given")
                .withNickNameFamilyName("Primary nickname family")
                .withNote("Primary note")
                .withColor("Primary color")
                .withMessageExpirationTimeVersion(2)
                .withHideStory(true)
                .withIsBlocked(false)
                .withBlockedAt(100)
                .withIsArchived(true)
                .withIsHidden(true)
                .withUnregisteredTimestamp(300L)
                .build();
        final var secondary = Contact.newBuilder()
                .withGivenName("Secondary given")
                .withFamilyName("Secondary family")
                .withNickName("Secondary system nickname")
                .withNickNameGivenName("Secondary nickname given")
                .withNickNameFamilyName("Secondary nickname family")
                .withNote("Secondary note")
                .withColor("Secondary color")
                .withMessageExpirationTime(60)
                .withMessageExpirationTimeVersion(3)
                .withMuteUntil(400)
                .withIsBlocked(true)
                .withBlockedAt(200)
                .withIsProfileSharingEnabled(true)
                .withIsHidden(true)
                .withUnregisteredTimestamp(500L)
                .build();

        final var merged = RecipientStore.mergeContacts(primary, secondary);

        assertEquals("Secondary given", merged.givenName());
        assertEquals("Secondary family", merged.familyName());
        assertEquals("Primary system nickname", merged.nickName());
        assertEquals("Primary nickname given", merged.nickNameGivenName());
        assertEquals("Primary nickname family", merged.nickNameFamilyName());
        assertEquals("Primary note", merged.note());
        assertEquals("Primary color", merged.color());
        assertEquals(60, merged.messageExpirationTime());
        assertEquals(3, merged.messageExpirationTimeVersion());
        assertEquals(400, merged.muteUntil());
        assertTrue(merged.hideStory());
        assertTrue(merged.isBlocked());
        assertEquals(200, merged.blockedAt());
        assertTrue(merged.isArchived());
        assertTrue(merged.isProfileSharingEnabled());
        assertFalse(merged.isHidden());
        assertEquals(300L, merged.unregisteredTimestamp());
    }

    @Test
    void mergeContactsPrefersConfiguredPrimaryValues() {
        final var primary = Contact.newBuilder()
                .withColor("Primary color")
                .withMessageExpirationTime(30)
                .withMessageExpirationTimeVersion(4)
                .withMuteUntil(100)
                .withIsHidden(true)
                .build();
        final var secondary = Contact.newBuilder()
                .withColor("Secondary color")
                .withMessageExpirationTime(60)
                .withMessageExpirationTimeVersion(3)
                .withMuteUntil(200)
                .build();

        final var merged = RecipientStore.mergeContacts(primary, secondary);

        assertEquals("Primary color", merged.color());
        assertEquals(30, merged.messageExpirationTime());
        assertEquals(4, merged.messageExpirationTimeVersion());
        assertEquals(100, merged.muteUntil());
        assertTrue(merged.isHidden());
    }
}
