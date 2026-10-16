package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.NotificationProfileOverride;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.storage.notificationProfiles.NotificationProfile;
import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.Recipient;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationProfileUtilsTest {

    @Test
    void formatsUuidIdsAsUuid() {
        final var uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        final var raw = NotificationProfileUtils.parseProfileId(uuid.toString()).orElseThrow();
        assertEquals(16, raw.length);
        assertEquals(uuid.toString(), NotificationProfileUtils.formatProfileId(raw));
    }

    @Test
    void formatsOtherIdsAsHex() {
        final var raw = new byte[]{1, 2, 3, (byte) 0xab};
        assertEquals("010203ab", NotificationProfileUtils.formatProfileId(raw));
        assertArrayEquals(raw, NotificationProfileUtils.parseProfileId("010203AB").orElseThrow());
        assertArrayEquals(raw, NotificationProfileUtils.parseProfileId("01:02:03:ab").orElseThrow());
    }

    @Test
    void rejectsNonIds() {
        assertTrue(NotificationProfileUtils.parseProfileId("Work").isEmpty());
        assertTrue(NotificationProfileUtils.parseProfileId("").isEmpty());
        assertTrue(NotificationProfileUtils.parseProfileId("abc").isEmpty());
    }

    @Test
    void overrideRoundTrip() {
        assertNull(NotificationProfileUtils.toProto(new NotificationProfileOverride.None()));
        assertInstanceOf(NotificationProfileOverride.None.class, NotificationProfileUtils.toApi((AccountRecord.NotificationProfileManualOverride) null));

        final var disabled = new NotificationProfileOverride.Disabled(1234L);
        final var disabledProto = NotificationProfileUtils.toProto(disabled);
        assertEquals(1234L, disabledProto.disabledAtTimestampMs);
        assertNull(disabledProto.enabled);
        assertEquals(disabled, NotificationProfileUtils.toApi(disabledProto));

        final var id = new byte[]{9, 8, 7};
        final var enabled = new NotificationProfileOverride.Enabled(id, 5678L);
        final var enabledProto = NotificationProfileUtils.toProto(enabled);
        assertNull(enabledProto.disabledAtTimestampMs);
        assertEquals(ByteString.of(id), enabledProto.enabled.id);
        assertEquals(5678L, enabledProto.enabled.endAtTimestampMs);
        final var back = assertInstanceOf(NotificationProfileOverride.Enabled.class,
                NotificationProfileUtils.toApi(enabledProto));
        assertArrayEquals(id, back.profileId());
        assertEquals(5678L, back.endAt());

        final var forever = new NotificationProfileOverride.Enabled(id, NotificationProfileOverride.END_NEVER);
        assertTrue(forever.isIndefinite());
        assertTrue(assertInstanceOf(NotificationProfileOverride.Enabled.class,
                NotificationProfileUtils.toApi(NotificationProfileUtils.toProto(forever))).isIndefinite());
    }

    @Test
    void convertsStoredProfileToApi() throws Exception {
        final var aci = ACI.from(UUID.randomUUID());
        final var id = ByteString.of(new byte[]{1, 2, 3, 4});
        final var proto = new org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.Builder().id(
                        id)
                .name("Work")
                .emoji("💼")
                .color(0xff00ff00)
                .createdAtMs(42L)
                .allowAllCalls(true)
                .allowAllMentions(false)
                .allowedMembers(List.of(new Recipient.Builder().contact(new Recipient.Contact.Builder().serviceId(aci.toString())
                                .build()).build(),
                        new Recipient.Builder().contact(new Recipient.Contact.Builder().e164("+12025550123").build())
                                .build()))
                .scheduleEnabled(true)
                .scheduleStartTime(900)
                .scheduleEndTime(1730)
                .scheduleDaysEnabled(List.of(org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.DayOfWeek.MONDAY,
                        org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.DayOfWeek.FRIDAY,
                        org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.DayOfWeek.UNKNOWN))
                .build();
        final var local = new NotificationProfile(1, id.toByteArray(), "Work", 0, null, proto.encode());

        final var api = NotificationProfileUtils.toApi(local);
        assertArrayEquals(id.toByteArray(), api.id());
        assertEquals("Work", api.name());
        assertEquals("💼", api.emoji().orElseThrow());
        assertEquals(0xff00ff00, api.color());
        assertEquals(42L, api.createdAt());
        assertTrue(api.allowAllCalls());
        assertEquals(List.of(new RecipientIdentifier.Uuid(aci.getRawUuid()),
                new RecipientIdentifier.Number("+12025550123")), api.allowedMembers());
        assertTrue(api.scheduleEnabled());
        assertEquals(900, api.scheduleStart());
        assertEquals(1730, api.scheduleEnd());
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), api.scheduleDaysEnabled());
    }
}
