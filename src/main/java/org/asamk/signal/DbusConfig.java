package org.asamk.signal;

public class DbusConfig {

    public DbusConfig() {
    }

    public String signalBusName = "org.asamk.Signal";
    public String signalObjectPath = "/org/asamk/Signal";

    public void setName(String busName) {
        DbusConfig.this.signalBusName = busName;
    }

    public String getName() {
        return DbusConfig.this.signalBusName;
    }

    public void setObjectPath(String objectPath) {
        DbusConfig.this.signalObjectPath = objectPath;
    }

    public String getObjectPath() {
        return DbusConfig.this.signalObjectPath;
    }
}
