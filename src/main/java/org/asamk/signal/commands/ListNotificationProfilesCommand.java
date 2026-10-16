package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.NotificationProfile;
import org.asamk.signal.manager.api.NotificationProfileOverride;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.util.NotificationProfileUtils;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.DateUtils;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ListNotificationProfilesCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "listNotificationProfiles";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show the notification profiles of this account and the current manual override.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var profiles = m.getNotificationProfiles();
        final var override = m.getNotificationProfileOverride();

        switch (outputWriter) {
            case JsonWriter writer -> writer.write(new JsonNotificationProfiles(JsonManualOverride.from(override),
                    profiles.stream().map(p -> new JsonNotificationProfile(p, override)).toList()));
            case PlainTextWriter writer -> {
                writer.println("Manual override: {}", describeOverride(override, profiles));
                for (final var profile : profiles) {
                    writer.println("Profile: “{}” Id: {} Emoji: {} Manually enabled: {} Schedule: {}",
                            profile.name(),
                            NotificationProfileUtils.formatProfileId(profile.id()),
                            profile.emoji().orElse(""),
                            isManuallyEnabled(profile, override),
                            describeSchedule(profile));
                    writer.indentedWriter()
                            .println("Allow all calls: {} Allow all mentions: {} Allowed members: {} Created: {}",
                                    profile.allowAllCalls(),
                                    profile.allowAllMentions(),
                                    profile.allowedMembers()
                                            .stream()
                                            .map(RecipientIdentifier::getIdentifier)
                                            .collect(Collectors.joining(", ")),
                                    DateUtils.formatTimestamp(profile.createdAt()));
                }
            }
        }
    }

    static boolean isManuallyEnabled(final NotificationProfile profile, final NotificationProfileOverride override) {
        return override instanceof NotificationProfileOverride.Enabled enabled && Arrays.equals(enabled.profileId(),
                profile.id());
    }

    static String describeOverride(
            final NotificationProfileOverride override,
            final List<NotificationProfile> profiles
    ) {
        return switch (override) {
            case NotificationProfileOverride.None _ -> "none";
            case NotificationProfileOverride.Disabled disabled ->
                    "disabled at " + DateUtils.formatTimestamp(disabled.disabledAt());
            case NotificationProfileOverride.Enabled enabled -> {
                final var name = profiles.stream()
                        .filter(p -> Arrays.equals(p.id(), enabled.profileId()))
                        .map(p -> "“" + p.name() + "”")
                        .findFirst()
                        .orElse("unknown profile");
                yield "enabled "
                        + name
                        + " ("
                        + NotificationProfileUtils.formatProfileId(enabled.profileId())
                        + ") "
                        + (enabled.isIndefinite()
                        ? "until turned off"
                        : "until " + DateUtils.formatTimestamp(enabled.endAt()));
            }
        };
    }

    private static String describeSchedule(final NotificationProfile profile) {
        if (!profile.scheduleEnabled()) {
            return "disabled";
        }
        return formatScheduleTime(profile.scheduleStart())
                + "-"
                + formatScheduleTime(profile.scheduleEnd())
                + " on "
                + profile.scheduleDaysEnabled()
                .stream()
                .map(d -> d.name().substring(0, 3).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    private static String formatScheduleTime(final int hhmm) {
        return "%02d:%02d".formatted(hhmm / 100, hhmm % 100);
    }

    private record JsonNotificationProfiles(JsonManualOverride manualOverride, List<JsonNotificationProfile> profiles) {}

    private record JsonManualOverride(String state, String profileId, Long endAt, Long disabledAt) {

        static JsonManualOverride from(final NotificationProfileOverride override) {
            return switch (override) {
                case NotificationProfileOverride.None _ -> new JsonManualOverride("none", null, null, null);
                case NotificationProfileOverride.Disabled disabled ->
                        new JsonManualOverride("disabled", null, null, disabled.disabledAt());
                case NotificationProfileOverride.Enabled enabled -> new JsonManualOverride("enabled",
                        NotificationProfileUtils.formatProfileId(enabled.profileId()),
                        enabled.isIndefinite() ? null : enabled.endAt(),
                        null);
            };
        }
    }

    private record JsonNotificationProfile(
            String id,
            String name,
            String emoji,
            int color,
            long createdAt,
            boolean manuallyEnabled,
            boolean allowAllCalls,
            boolean allowAllMentions,
            List<String> allowedMembers,
            boolean scheduleEnabled,
            String scheduleStart,
            String scheduleEnd,
            List<DayOfWeek> scheduleDaysEnabled
    ) {

        JsonNotificationProfile(final NotificationProfile profile, final NotificationProfileOverride override) {
            this(NotificationProfileUtils.formatProfileId(profile.id()),
                    profile.name(),
                    profile.emoji().orElse(null),
                    profile.color(),
                    profile.createdAt(),
                    isManuallyEnabled(profile, override),
                    profile.allowAllCalls(),
                    profile.allowAllMentions(),
                    profile.allowedMembers().stream().map(RecipientIdentifier::getIdentifier).toList(),
                    profile.scheduleEnabled(),
                    formatScheduleTime(profile.scheduleStart()),
                    formatScheduleTime(profile.scheduleEnd()),
                    profile.scheduleDaysEnabled());
        }
    }
}
