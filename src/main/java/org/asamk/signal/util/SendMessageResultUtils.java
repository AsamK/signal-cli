package org.asamk.signal.util;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonSendMessageResult;
import org.asamk.signal.manager.api.ProofRequiredException;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SendMessageResultUtils {

    private SendMessageResultUtils() {
    }

    public static void outputResult(final OutputWriter outputWriter, final SendGroupMessageResults sendMessageResults) {
        if (outputWriter instanceof PlainTextWriter writer) {
            var errors = getErrorMessagesFromSendMessageResults(sendMessageResults.results());
            printSendMessageResultErrors(writer, errors);
            writer.println("{}", sendMessageResults.timestamp());
        } else {
            final var writer = (JsonWriter) outputWriter;
            var results = getJsonSendMessageResults(sendMessageResults.results());
            writer.write(Map.of("timestamp", sendMessageResults.timestamp(), "results", results));
        }
    }

    public static void outputResult(
            final OutputWriter outputWriter, final SendMessageResults sendMessageResults
    ) throws CommandException {
        if (outputWriter instanceof PlainTextWriter writer) {
            var errors = getErrorMessagesFromSendMessageResults(sendMessageResults.results());
            printSendMessageResultErrors(writer, errors);
            writer.println("{}", sendMessageResults.timestamp());
        } else {
            final var writer = (JsonWriter) outputWriter;
            var results = getJsonSendMessageResults(sendMessageResults.results());
            writer.write(Map.of("timestamp", sendMessageResults.timestamp(), "results", results));
        }
        if (!sendMessageResults.hasSuccess()) {
            if (sendMessageResults.hasOnlyUntrustedIdentity()) {
                throw new UntrustedKeyErrorException("Failed to send message due to untrusted identities");
            } else {
                throw new UserErrorException("Failed to send message");
            }
        }
    }

    public static List<String> getErrorMessagesFromSendMessageResults(final Map<RecipientIdentifier, List<SendMessageResult>> mapResults) {
        return mapResults.entrySet()
                .stream()
                .flatMap(entry -> entry.getValue()
                        .stream()
                        .map(SendMessageResultUtils::getErrorMessageFromSendMessageResult)
                        .filter(Objects::nonNull)
                        .map(error -> entry.getKey().getIdentifier() + ": " + error))
                .toList();
    }

    public static List<String> getErrorMessagesFromSendMessageResults(Collection<SendMessageResult> results) {
        return results.stream()
                .map(SendMessageResultUtils::getErrorMessageFromSendMessageResult)
                .filter(Objects::nonNull)
                .toList();
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

    public static void printSendMessageResultErrors(PlainTextWriter writer, List<String> errors) {
        if (errors.size() == 0) {
            return;
        }
        writer.println("Failed to send (some) messages:");
        for (var error : errors) {
            writer.println(error);
        }
    }

    private static List<JsonSendMessageResult> getJsonSendMessageResults(final Map<RecipientIdentifier, List<SendMessageResult>> mapResults) {
        return mapResults.entrySet().stream().flatMap(entry -> {
            final var groupId = entry.getKey() instanceof RecipientIdentifier.Group g ? g.groupId() : null;
            return entry.getValue().stream().map(r -> JsonSendMessageResult.from(r, groupId));
        }).toList();
    }

    public static List<JsonSendMessageResult> getJsonSendMessageResults(Collection<SendMessageResult> results) {
        return results.stream().map(JsonSendMessageResult::from).toList();
    }
}
