package org.asamk.signal.util;

import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class ErrorUtils {

    private final static Logger logger = LoggerFactory.getLogger(ErrorUtils.class);

    private ErrorUtils() {
    }

    public static void handleTimestampAndSendMessageResults(
            PlainTextWriter writer, long timestamp, List<SendMessageResult> results
    ) throws CommandException {
        if (timestamp != 0) {
            writer.println("{}", timestamp);
        }
        var errors = getErrorMessagesFromSendMessageResults(results);
        handleSendMessageResultErrors(errors);
    }

    public static List<String> getErrorMessagesFromSendMessageResults(List<SendMessageResult> results) {
        var errors = new ArrayList<String>();
        for (var result : results) {
            var error = getErrorMessageFromSendMessageResult(result);
            if (error != null) {
                errors.add(error);
            }
        }

        return errors;
    }

    public static String getErrorMessageFromSendMessageResult(SendMessageResult result) {
        var identifier = getLegacyIdentifier(result.getAddress());
        if (result.isNetworkFailure()) {
            return String.format("Network failure for \"%s\"", identifier);
        } else if (result.isUnregisteredFailure()) {
            return String.format("Unregistered user \"%s\"", identifier);
        } else if (result.getIdentityFailure() != null) {
            return String.format("Untrusted Identity for \"%s\"", identifier);
        } else if (result.getProofRequiredFailure() != null) {
            final var failure = result.getProofRequiredFailure();
            return String.format(
                    "CAPTCHA proof required for sending to \"%s\", available options \"%s\" with token \"%s\", or wait \"%d\" seconds",
                    identifier,
                    failure.getOptions()
                            .stream()
                            .map(ProofRequiredException.Option::toString)
                            .collect(Collectors.joining(", ")),
                    failure.getToken(),
                    failure.getRetryAfterSeconds());
        }
        return null;
    }

    private static void handleSendMessageResultErrors(List<String> errors) throws CommandException {
        if (errors.size() == 0) {
            return;
        }
        var message = new StringBuilder();
        message.append("Failed to send (some) messages:\n");
        for (var error : errors) {
            message.append(error).append("\n");
        }
        throw new IOErrorException(message.toString());
    }
}
