package org.asamk.signal.util;

import org.asamk.signal.GroupIdFormatException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;

public class ErrorUtils {

    private ErrorUtils() {
    }

    public static void handleAssertionError(AssertionError e) {
        System.err.println("Failed to send/receive message (Assertion): " + e.getMessage());
        e.printStackTrace();
        System.err.println("If you use an Oracle JRE please check if you have unlimited strength crypto enabled, see README");
    }

    public static void handleEncapsulatedExceptions(EncapsulatedExceptions e) {
        System.err.println("Failed to send (some) messages:");
        for (NetworkFailureException n : e.getNetworkExceptions()) {
            System.err.println("Network failure for \"" + n.getE164number() + "\": " + n.getMessage());
        }
        for (UnregisteredUserException n : e.getUnregisteredUserExceptions()) {
            System.err.println("Unregistered user \"" + n.getE164Number() + "\": " + n.getMessage());
        }
        for (UntrustedIdentityException n : e.getUntrustedIdentityExceptions()) {
            System.err.println("Untrusted Identity for \"" + n.getIdentifier() + "\": " + n.getMessage());
        }
    }

    public static void handleIOException(IOException e) {
        System.err.println("Failed to send message: " + e.getMessage());
    }

    public static void handleGroupNotFoundException(GroupNotFoundException e) {
        System.err.println("Failed to send to group: " + e.getMessage());
        System.err.println("Aborting sending.");
    }

    public static void handleNotAGroupMemberException(NotAGroupMemberException e) {
        System.err.println("Failed to send to group: " + e.getMessage());
        System.err.println("Update the group on another device to readd the user to this group.");
        System.err.println("Aborting sending.");
    }

    public static void handleDBusExecutionException(DBusExecutionException e) {
        System.err.println("Cannot connect to dbus: " + e.getMessage());
        System.err.println("Aborting.");
    }

    public static void handleGroupIdFormatException(GroupIdFormatException e) {
        System.err.println(e.getMessage());
        System.err.println("Aborting sending.");
    }
}
