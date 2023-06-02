package org.asamk.signal.dbus;

public final class DbusUtils {

    private DbusUtils() {

    }

    public static String makeValidObjectPathElement(String pathElement) {
        return pathElement.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
