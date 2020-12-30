package org.asamk.signal.util;

import org.asamk.signal.manager.GroupIdFormatException;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.NotAGroupMemberException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ErrorUtils {

    private ErrorUtils() {
    }

    public static void handleAssertionError(AssertionError e) {
        System.err.println("Failed to send/receive message (Assertion): " + e.getMessage());
        e.printStackTrace();
        System.err.println(
                "If you use an Oracle JRE please check if you have unlimited strength crypto enabled, see README");
    }

    public static int handleTimestampAndSendMessageResults(long timestamp, List<SendMessageResult> results) {
        if (timestamp != 0) {
            System.out.println(timestamp);
        }
        List<String> errors = getErrorMessagesFromSendMessageResults(results);
        return handleSendMessageResultErrors(errors);
    }

    public static List<String> getErrorMessagesFromSendMessageResults(List<SendMessageResult> results) {
        List<String> errors = new ArrayList<>();
        for (SendMessageResult result : results) {
            if (result.isNetworkFailure()) {
                errors.add(String.format("Network failure for \"%s\"", result.getAddress().getLegacyIdentifier()));
            } else if (result.isUnregisteredFailure()) {
                errors.add(String.format("Unregistered user \"%s\"", result.getAddress().getLegacyIdentifier()));
            } else if (result.getIdentityFailure() != null) {
                errors.add(String.format("Untrusted Identity for \"%s\"", result.getAddress().getLegacyIdentifier()));
            }
        }

        return errors;
    }

    private static int handleSendMessageResultErrors(List<String> errors) {
        if (errors.size() == 0) {
            return 0;
        }
        System.err.println("Failed to send (some) messages:");
        for (String error : errors) {
            System.err.println(error);
        }
        return 3;
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

    public static void handleGroupIdFormatException(GroupIdFormatException e) {
        System.err.println(e.getMessage());
        System.err.println("Aborting sending.");
    }

    public static void handleInvalidNumberException(InvalidNumberException e) {
        System.err.println("Failed to parse recipient: " + e.getMessage());
        System.err.println("Aborting sending.");
    }
}
