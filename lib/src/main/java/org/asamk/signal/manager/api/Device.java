package org.asamk.signal.manager.api;

public record Device(long id, String name, long created, long lastSeen, boolean isThisDevice) {}
