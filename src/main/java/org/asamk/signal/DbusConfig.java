package org.asamk.signal;

import org.asamk.signal.dbus.DbusUtils;

import java.io.File;

public class DbusConfig {

    private static final String SIGNAL_BUSNAME = "org.asamk.Signal";
    private static final String SIGNAL_BUSNAME_FLATPAK = "org.asamk.SignalCli";
    private static final String SIGNAL_OBJECT_BASE_PATH = "/org/asamk/Signal";

    public static String getBusname() {
        if (new File("/.flatpak-info").exists()) {
            return SIGNAL_BUSNAME_FLATPAK;
        } else {
            return SIGNAL_BUSNAME;
        }
    }

    public static String getObjectPath() {
        return getObjectPath(null);
    }

    public static String getObjectPath(String account) {
        if (account == null) {
            return SIGNAL_OBJECT_BASE_PATH;
        }

        return SIGNAL_OBJECT_BASE_PATH + "/" + DbusUtils.makeValidObjectPathElement(account);
    }
}
