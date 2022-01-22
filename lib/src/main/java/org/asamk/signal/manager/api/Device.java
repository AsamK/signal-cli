package org.asamk.signal.manager.api;

public record Device(int id, String name, long created, long lastSeen, boolean isThisDevice) {}
