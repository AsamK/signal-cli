package org.asamk.signal.manager.api;

public enum PhoneNumberSharingMode {
    EVERYBODY,
    CONTACTS,
    NOBODY;

    public static PhoneNumberSharingMode valueOfOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
