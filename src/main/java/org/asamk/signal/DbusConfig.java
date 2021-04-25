package org.asamk.signal;

public class DbusConfig {

    private static final String SIGNAL_BUSNAME = "org.asamk.Signal";
    private static final String SIGNAL_OBJECT_BASE_PATH = "/org/asamk/Signal";

    public static String getBusname() {
        return SIGNAL_BUSNAME;
    }

    public static String getObjectPath() {
        return getObjectPath(null);
    }

    public static String getObjectPath(String username) {
        if (username == null) {
            return SIGNAL_OBJECT_BASE_PATH;
        }

        return SIGNAL_OBJECT_BASE_PATH + "/" + username.replace('+', '_');
    }
}
