package org.asamk.signal.manager.api;

public class Device {

    private final long id;
    private final String name;
    private final long created;
    private final long lastSeen;
    private final boolean thisDevice;

    public Device(long id, String name, long created, long lastSeen, final boolean thisDevice) {
        this.id = id;
        this.name = name;
        this.created = created;
        this.lastSeen = lastSeen;
        this.thisDevice = thisDevice;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCreated() {
        return created;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public boolean isThisDevice() {
        return thisDevice;
    }
}
