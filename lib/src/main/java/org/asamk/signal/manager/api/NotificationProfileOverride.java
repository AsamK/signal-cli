package org.asamk.signal.manager.api;

/**
 * The manual override state for notification profiles, shared between all devices of an account.
 * <p>
 * Without an override the official clients activate profiles purely according to their schedule.
 */
public sealed interface NotificationProfileOverride {

    /**
     * No manual override, the schedule (if any) decides which profile is active.
     */
    record None() implements NotificationProfileOverride {}

    /**
     * A profile was manually turned off.
     * The official clients keep scheduled profiles off until the schedule window that was active at
     * {@code disabledAt} has passed.
     *
     * @param disabledAt Timestamp in milliseconds since epoch.
     */
    record Disabled(long disabledAt) implements NotificationProfileOverride {}

    /**
     * A profile was manually turned on.
     *
     * @param profileId The id of the enabled profile.
     * @param endAt     Timestamp in milliseconds since epoch at which the profile is turned off again,
     *                  or {@link #END_NEVER} to keep it on until it is manually turned off.
     */
    record Enabled(byte[] profileId, long endAt) implements NotificationProfileOverride {

        public boolean isIndefinite() {
            return endAt == END_NEVER;
        }
    }

    /**
     * Sentinel end timestamp meaning "until manually turned off".
     */
    long END_NEVER = Long.MAX_VALUE;
}
