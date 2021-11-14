package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SendCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(SendCommand.class);

    @Override
    public String getName() {
        return "send";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a message to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("--note-to-self")
                .help("Send the message to self without notification.")
                .action(Arguments.storeTrue());

        subparser.addArgument("-m", "--message").help("Specify the message, if missing standard input is used.");
        subparser.addArgument("-a", "--attachment").nargs("*").help("Add file as attachment");
        subparser.addArgument("-e", "--end-session", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--mention")
                .nargs("*")
                .help("Mention another group member (syntax: start:length:recipientNumber)");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var isNoteToSelf = Boolean.TRUE.equals(ns.getBoolean("note-to-self"));
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                isNoteToSelf,
                recipientStrings,
                groupIdStrings);

        final var isEndSession = Boolean.TRUE.equals(ns.getBoolean("end-session"));
        if (isEndSession) {
            final var singleRecipients = recipientIdentifiers.stream()
                    .filter(r -> r instanceof RecipientIdentifier.Single)
                    .map(RecipientIdentifier.Single.class::cast)
                    .collect(Collectors.toSet());
            if (singleRecipients.isEmpty()) {
                throw new UserErrorException("No recipients given");
            }

            try {
                final var results = m.sendEndSessionMessage(singleRecipients);
                outputResult(outputWriter, results.timestamp());
                ErrorUtils.handleSendMessageResults(results.results());
                return;
            } catch (IOException e) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
            }
        }

        var messageText = ns.getString("message");
        if (messageText == null) {
            try {
                messageText = IOUtils.readAll(System.in, Charset.defaultCharset());
            } catch (IOException e) {
                throw new UserErrorException("Failed to read message from stdin: " + e.getMessage());
            }
        }

        List<String> attachments = ns.getList("attachment");
        if (attachments == null) {
            attachments = List.of();
        }

        List<String> mentionStrings = ns.getList("mention");
        List<Message.Mention> mentions;
        if (mentionStrings == null) {
            mentions = List.of();
        } else {
            final Pattern mentionPattern = Pattern.compile("([0-9]+):([0-9]+):(.+)");
            mentions = new ArrayList<>();
            for (final var mention : mentionStrings) {
                final var matcher = mentionPattern.matcher(mention);
                if (!matcher.matches()) {
                    throw new UserErrorException("Invalid mention syntax ("
                            + mention
                            + ") expected 'start:end:recipientNumber'");
                }
                mentions.add(new Message.Mention(CommandUtil.getSingleRecipientIdentifier(matcher.group(3),
                        m.getSelfNumber()), Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
            }
        }

        try {
            var results = m.sendMessage(new Message(messageText, attachments, mentions), recipientIdentifiers);
            outputResult(outputWriter, results.timestamp());
            ErrorUtils.handleSendMessageResults(results.results());
        } catch (AttachmentInvalidException | IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        }
    }

    private void outputResult(final OutputWriter outputWriter, final long timestamp) {
        if (outputWriter instanceof PlainTextWriter writer) {
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }
}
