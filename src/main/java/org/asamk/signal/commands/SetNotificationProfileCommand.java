package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.NotificationProfile;
import org.asamk.signal.manager.api.NotificationProfileNotFoundException;
import org.asamk.signal.manager.api.NotificationProfileOverride;
import org.asamk.signal.manager.util.NotificationProfileUtils;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.TimeParser;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SetNotificationProfileCommand implements JsonRpcLocalCommand {

    private static final Set<String> FOREVER_KEYWORDS = Set.of("forever", "indefinitely", "never", "always", "manual");

    @Override
    public String getName() {
        return "setNotificationProfile";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Manually turn a notification profile on or off, synced to all devices.");
        subparser.addArgument("-p", "--profile")
                .help("Name or id of the notification profile to turn on (required with --for / --until).");
        final var mut = subparser.addMutuallyExclusiveGroup().required(true);
        mut.addArgument("--for")
                .dest("for")
                .help("Turn the profile on for a duration, e.g. \"90m\", \"1h37m\", \"2 hours 30 minutes\", \"1:30\", \"PT2H\".");
        mut.addArgument("--until")
                .help("Turn the profile on until a point in time, e.g. \"18:00\", \"6:30pm\", \"tomorrow 9:00\", \"2026-12-24T18:00\", a unix timestamp in seconds or milliseconds, or \"forever\" to keep it on until turned off manually.");
        mut.addArgument("--disable")
                .action(Arguments.storeTrue())
                .help("Turn the currently active profile off. A scheduled profile stays off until its current schedule window ends.");
        mut.addArgument("--clear")
                .action(Arguments.storeTrue())
                .help("Remove the manual override, so profiles are activated by their schedule again.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var profileArg = ns.getString("profile");
        final var forArg = ns.getString("for");
        final var untilArg = ns.getString("until");
        final var disable = Boolean.TRUE.equals(ns.getBoolean("disable"));
        final var clear = Boolean.TRUE.equals(ns.getBoolean("clear"));

        final var clock = Clock.systemDefaultZone();
        final NotificationProfileOverride override;
        NotificationProfile profile = null;
        if (disable || clear) {
            if (profileArg != null) {
                throw new UserErrorException("--profile can't be combined with --disable or --clear");
            }
            override = disable
                    ? new NotificationProfileOverride.Disabled(clock.millis())
                    : new NotificationProfileOverride.None();
        } else {
            if (profileArg == null) {
                throw new UserErrorException("--profile is required to turn a notification profile on");
            }
            profile = findProfile(m.getNotificationProfiles(), profileArg);
            final long endAt;
            if (forArg != null) {
                endAt = clock.millis() + parseDuration(forArg).toMillis();
            } else {
                endAt = parseUntil(untilArg, clock);
            }
            override = new NotificationProfileOverride.Enabled(profile.id(), endAt);
        }

        try {
            m.setNotificationProfileOverride(override);
        } catch (NotificationProfileNotFoundException e) {
            throw new UserErrorException(e.getMessage());
        }

        switch (outputWriter) {
            case JsonWriter writer -> writer.write(toJson(override, profile));
            case PlainTextWriter writer -> writer.println("{}", describe(override, profile, clock));
        }
    }

    static NotificationProfile findProfile(
            final List<NotificationProfile> profiles,
            final String identifier
    ) throws UserErrorException {
        if (profiles.isEmpty()) {
            throw new UserErrorException(
                    "No notification profiles known. Create one in a Signal app and make sure storage sync has run (e.g. via receive).");
        }
        final var idBytes = NotificationProfileUtils.parseProfileId(identifier);
        if (idBytes.isPresent()) {
            final var byId = profiles.stream().filter(p -> Arrays.equals(p.id(), idBytes.get())).findFirst();
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        final var byName = profiles.stream()
                .filter(p -> p.name().equalsIgnoreCase(identifier.trim()))
                .toList();
        if (byName.size() == 1) {
            return byName.getFirst();
        }
        if (byName.size() > 1) {
            throw new UserErrorException("Multiple notification profiles are named \""
                    + identifier
                    + "\", use the id instead: "
                    + byName.stream()
                    .map(p -> NotificationProfileUtils.formatProfileId(p.id()))
                    .collect(Collectors.joining(", ")));
        }
        throw new UserErrorException("Notification profile not found: \""
                + identifier
                + "\". Known profiles: "
                + profiles.stream()
                .map(p -> "\"" + p.name() + "\" (" + NotificationProfileUtils.formatProfileId(p.id()) + ")")
                .collect(Collectors.joining(", ")));
    }

    static Duration parseDuration(final String forArg) throws UserErrorException {
        try {
            return TimeParser.parseDuration(forArg);
        } catch (TimeParser.TimeParseException e) {
            throw new UserErrorException("Invalid --for value: " + e.getMessage());
        }
    }

    static long parseUntil(final String untilArg, final Clock clock) throws UserErrorException {
        if (FOREVER_KEYWORDS.contains(untilArg.trim().toLowerCase(Locale.ROOT))) {
            return NotificationProfileOverride.END_NEVER;
        }
        final long endAt;
        try {
            endAt = TimeParser.parseTimestamp(untilArg, clock);
        } catch (TimeParser.TimeParseException e) {
            if (TimeParser.looksLikeDuration(untilArg)) {
                throw new UserErrorException("Invalid --until value: \""
                        + untilArg
                        + "\" looks like a duration, use --for instead");
            }
            throw new UserErrorException("Invalid --until value: " + e.getMessage());
        }
        if (endAt <= clock.millis()) {
            throw new UserErrorException("Invalid --until value: "
                    + DateUtils.formatTimestamp(endAt)
                    + " is in the past");
        }
        return endAt;
    }

    private static String describe(
            final NotificationProfileOverride override,
            final NotificationProfile profile,
            final Clock clock
    ) {
        return switch (override) {
            case NotificationProfileOverride.None _ -> "Cleared manual notification profile override";
            case NotificationProfileOverride.Disabled disabled ->
                    "Disabled notification profile at " + DateUtils.formatTimestamp(disabled.disabledAt());
            case NotificationProfileOverride.Enabled enabled -> {
                final var name = profile == null ? "" : "“" + profile.name() + "” ";
                final var id = NotificationProfileUtils.formatProfileId(enabled.profileId());
                if (enabled.isIndefinite()) {
                    yield "Enabled notification profile " + name + "(" + id + ") until turned off";
                }
                final var remaining = Duration.ofMillis(enabled.endAt() - clock.millis());
                yield "Enabled notification profile "
                        + name
                        + "("
                        + id
                        + ") until "
                        + DateUtils.formatTimestamp(enabled.endAt())
                        + ", that's in "
                        + TimeParser.formatDuration(remaining);
            }
        };
    }

    private static Map<String, Object> toJson(
            final NotificationProfileOverride override,
            final NotificationProfile profile
    ) {
        return switch (override) {
            case NotificationProfileOverride.None _ -> Map.of("state", "none");
            case NotificationProfileOverride.Disabled disabled ->
                    Map.of("state", "disabled", "disabledAt", disabled.disabledAt());
            case NotificationProfileOverride.Enabled enabled -> {
                final var result = new java.util.LinkedHashMap<String, Object>();
                result.put("state", "enabled");
                result.put("profileId", NotificationProfileUtils.formatProfileId(enabled.profileId()));
                if (profile != null) {
                    result.put("profileName", profile.name());
                }
                result.put("endAt", enabled.isIndefinite() ? null : enabled.endAt());
                yield result;
            }
        };
    }
}
