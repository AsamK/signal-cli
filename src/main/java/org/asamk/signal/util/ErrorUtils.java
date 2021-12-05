package org.asamk.signal.util;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.api.ProofRequiredException;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ErrorUtils {

    private ErrorUtils() {
    }

    public static void handleSendMessageResults(
            Map<RecipientIdentifier, List<SendMessageResult>> mapResults
    ) throws CommandException {
        List<String> errors = getErrorMessagesFromSendMessageResults(mapResults);
        handleSendMessageResultErrors(errors);
    }

    public static void handleSendMessageResults(
            Collection<SendMessageResult> results
    ) throws CommandException {
        var errors = getErrorMessagesFromSendMessageResults(results);
        handleSendMessageResultErrors(errors);
    }

    public static List<String> getErrorMessagesFromSendMessageResults(final Map<RecipientIdentifier, List<SendMessageResult>> mapResults) {
        return mapResults.values()
                .stream()
                .flatMap(results -> getErrorMessagesFromSendMessageResults(results).stream())
                .collect(Collectors.toList());
    }

    public static List<String> getErrorMessagesFromSendMessageResults(Collection<SendMessageResult> results) {
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
        var identifier = result.address().getLegacyIdentifier();
        if (result.proofRequiredFailure() != null) {
            final var failure = result.proofRequiredFailure();
            return String.format(
                    "CAPTCHA proof required for sending to \"%s\", available options \"%s\" with challenge token \"%s\", or wait \"%d\" seconds.\n"
                            + (
                            failure.getOptions().contains(ProofRequiredException.Option.RECAPTCHA)
                                    ? """
                                    To get the captcha token, go to https://signalcaptchas.org/challenge/generate.html
                                    Check the developer tools (F12) console for a failed redirect to signalcaptcha://
                                    Everything after signalcaptcha:// is the captcha token.
                                    Use the following command to submit the captcha token:
                                    signal-cli submitRateLimitChallenge --challenge CHALLENGE_TOKEN --captcha CAPTCHA_TOKEN"""
                                    : ""
                    ),
                    identifier,
                    failure.getOptions()
                            .stream()
                            .map(ProofRequiredException.Option::toString)
                            .collect(Collectors.joining(", ")),
                    failure.getToken(),
                    failure.getRetryAfterSeconds());
        } else if (result.isNetworkFailure()) {
            return String.format("Network failure for \"%s\"", identifier);
        } else if (result.isUnregisteredFailure()) {
            return String.format("Unregistered user \"%s\"", identifier);
        } else if (result.isIdentityFailure()) {
            return String.format("Untrusted Identity for \"%s\"", identifier);
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
        throw new IOErrorException(message.toString(), null);
    }
}
