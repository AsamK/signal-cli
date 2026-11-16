package org.asamk.signal.manager.api;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * A notification profile, as configured in one of the official clients and synced via storage service.
 *
 * @param id                  The raw profile id, unique per account.
 * @param name                The user visible profile name.
 * @param emoji               Optional emoji shown next to the name.
 * @param color               The color as ARGB integer.
 * @param createdAt           Creation timestamp in milliseconds since epoch.
 * @param allowAllCalls       If true, calls from everyone are allowed while the profile is active.
 * @param allowAllMentions    If true, mentions from everyone are allowed while the profile is active.
 * @param allowedMembers      Contacts and groups whose notifications are still shown while the profile is active.
 * @param scheduleEnabled     If true, the profile is activated automatically according to the schedule.
 * @param scheduleStart       Schedule start time as HHMM (e.g. 900 for 9:00, 2230 for 22:30).
 * @param scheduleEnd         Schedule end time as HHMM.
 * @param scheduleDaysEnabled Days of the week the schedule is active on.
 */
public record NotificationProfile(
        byte[] id,
        String name,
        Optional<String> emoji,
        int color,
        long createdAt,
        boolean allowAllCalls,
        boolean allowAllMentions,
        List<RecipientIdentifier> allowedMembers,
        boolean scheduleEnabled,
        int scheduleStart,
        int scheduleEnd,
        List<DayOfWeek> scheduleDaysEnabled
) {}
