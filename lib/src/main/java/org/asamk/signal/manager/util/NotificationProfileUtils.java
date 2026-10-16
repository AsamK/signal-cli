package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.NotificationProfile;
import org.asamk.signal.manager.api.NotificationProfileOverride;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.groups.GroupUtils;
import org.signal.core.models.ServiceId;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.Recipient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import okio.ByteString;

public final class NotificationProfileUtils {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProfileUtils.class);

    private NotificationProfileUtils() {
    }

    public static NotificationProfile toApi(
            final org.asamk.signal.manager.storage.notificationProfiles.NotificationProfile local
    ) throws IOException {
        final var proto = org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.ADAPTER.decode(
                local.storageRecord());
        return new NotificationProfile(local.profileId(),
                proto.name,
                Optional.ofNullable(proto.emoji).filter(e -> !e.isEmpty()),
                proto.color,
                proto.createdAtMs,
                proto.allowAllCalls,
                proto.allowAllMentions,
                toRecipientIdentifiers(proto.allowedMembers),
                proto.scheduleEnabled,
                proto.scheduleStartTime,
                proto.scheduleEndTime,
                proto.scheduleDaysEnabled.stream().map(NotificationProfileUtils::toDayOfWeek).flatMap(Optional::stream).toList());
    }

    public static NotificationProfileOverride toApi(final AccountRecord.NotificationProfileManualOverride override) {
        if (override == null) {
            return new NotificationProfileOverride.None();
        }
        if (override.enabled != null) {
            return new NotificationProfileOverride.Enabled(override.enabled.id.toByteArray(),
                    override.enabled.endAtTimestampMs);
        }
        if (override.disabledAtTimestampMs != null) {
            return new NotificationProfileOverride.Disabled(override.disabledAtTimestampMs);
        }
        return new NotificationProfileOverride.None();
    }

    public static AccountRecord.NotificationProfileManualOverride toProto(final NotificationProfileOverride override) {
        return switch (override) {
            case NotificationProfileOverride.None _ -> null;
            case NotificationProfileOverride.Disabled disabled ->
                    new AccountRecord.NotificationProfileManualOverride.Builder().disabledAtTimestampMs(disabled.disabledAt())
                            .build();
            case NotificationProfileOverride.Enabled enabled ->
                    new AccountRecord.NotificationProfileManualOverride.Builder().enabled(new AccountRecord.NotificationProfileManualOverride.ManuallyEnabled.Builder().id(
                            ByteString.of(enabled.profileId())).endAtTimestampMs(enabled.endAt()).build()).build();
        };
    }

    /**
     * Format a profile id for display. Official clients use UUIDs, anything else is shown as hex.
     */
    public static String formatProfileId(final byte[] id) {
        if (id.length == 16) {
            final var buffer = ByteBuffer.wrap(id);
            return new UUID(buffer.getLong(), buffer.getLong()).toString();
        }
        return HexFormat.of().formatHex(id);
    }

    /**
     * Parse a profile id as given by the user, either as UUID or as hex string.
     *
     * @return the raw id, or empty if the input is neither a UUID nor a hex string
     */
    public static Optional<byte[]> parseProfileId(final String id) {
        final var trimmed = id.trim();
        try {
            final var uuid = UUID.fromString(trimmed);
            final var buffer = ByteBuffer.allocate(16);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
            return Optional.of(buffer.array());
        } catch (IllegalArgumentException ignored) {
        }
        final var hex = trimmed.replace(":", "").replace(" ", "");
        if (hex.isEmpty() || hex.length() % 2 != 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(HexFormat.of().parseHex(hex));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static List<RecipientIdentifier> toRecipientIdentifiers(final List<Recipient> recipients) {
        final var result = new ArrayList<RecipientIdentifier>();
        for (final var recipient : recipients) {
            if (recipient.groupMasterKey != null && recipient.groupMasterKey.size() > 0) {
                try {
                    final var masterKey = new GroupMasterKey(recipient.groupMasterKey.toByteArray());
                    result.add(new RecipientIdentifier.Group(GroupUtils.getGroupIdV2(masterKey)));
                } catch (InvalidInputException e) {
                    logger.debug("Ignoring notification profile member with invalid group master key");
                }
            } else if (recipient.legacyGroupId != null && recipient.legacyGroupId.size() > 0) {
                result.add(new RecipientIdentifier.Group(new GroupIdV1(recipient.legacyGroupId.toByteArray())));
            } else if (recipient.contact != null) {
                final var contact = recipient.contact;
                ServiceId serviceId = null;
                if (contact.serviceIdBinary != null && contact.serviceIdBinary.size() > 0) {
                    serviceId = ServiceId.parseOrNull(contact.serviceIdBinary.toByteArray());
                }
                if (serviceId == null && contact.serviceId != null && !contact.serviceId.isEmpty()) {
                    serviceId = ServiceId.parseOrNull(contact.serviceId);
                }
                if (serviceId instanceof ACI aci) {
                    result.add(new RecipientIdentifier.Uuid(aci.getRawUuid()));
                } else if (serviceId instanceof PNI pni) {
                    result.add(new RecipientIdentifier.Pni(pni.getRawUuid()));
                } else if (contact.e164 != null && !contact.e164.isEmpty()) {
                    result.add(new RecipientIdentifier.Number(contact.e164));
                } else {
                    logger.debug("Ignoring notification profile member without usable identifier");
                }
            }
        }
        return result;
    }

    private static Optional<DayOfWeek> toDayOfWeek(
            final org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.DayOfWeek day
    ) {
        return switch (day) {
            case MONDAY -> Optional.of(DayOfWeek.MONDAY);
            case TUESDAY -> Optional.of(DayOfWeek.TUESDAY);
            case WEDNESDAY -> Optional.of(DayOfWeek.WEDNESDAY);
            case THURSDAY -> Optional.of(DayOfWeek.THURSDAY);
            case FRIDAY -> Optional.of(DayOfWeek.FRIDAY);
            case SATURDAY -> Optional.of(DayOfWeek.SATURDAY);
            case SUNDAY -> Optional.of(DayOfWeek.SUNDAY);
            case UNKNOWN -> Optional.empty();
        };
    }
}
