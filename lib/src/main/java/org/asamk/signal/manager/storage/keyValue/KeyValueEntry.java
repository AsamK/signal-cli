package org.asamk.signal.manager.storage.keyValue;

public record KeyValueEntry<T>(String key, Class<T> clazz, T defaultValue) {

    public KeyValueEntry(String key, Class<T> clazz) {
        this(key, clazz, null);
    }
}
