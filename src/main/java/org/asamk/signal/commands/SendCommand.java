package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

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

        var mut = subparser.addMutuallyExclusiveGroup();
        mut.addArgument("-m", "--message").help("Specify the message to be sent.");
        mut.addArgument("--message-from-stdin")
                .action(Arguments.storeTrue())
                .help("Read the message from standard input.");
        subparser.addArgument("-a", "--attachment")
                .nargs("*")
                .help("Add an attachment. "
                        + "Can be either a file path or a data URI. Data URI encoded attachments must follow the RFC 2397. Additionally a file name can be added, e.g. "
                        + "data:<MIME-TYPE>;filename=<FILENAME>;base64,<BASE64 ENCODED DATA>.");
        subparser.addArgument("-e", "--end-session", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--mention")
                .nargs("*")
                .help("Mention another group member (syntax: start:length:recipientNumber)");
        subparser.addArgument("--quote-timestamp")
                .type(long.class)
                .help("Specify the timestamp of a previous message with the recipient or group to add a quote to the new message.");
        subparser.addArgument("--quote-author").help("Specify the number of the author of the original message.");
        subparser.addArgument("--quote-message").help("Specify the message of the original message.");
        subparser.addArgument("--quote-mention")
                .nargs("*")
                .help("Quote with mention of another group member (syntax: start:length:recipientNumber)");
        subparser.addArgument("--sticker").help("Send a sticker (syntax: stickerPackId:stickerId)");
        subparser.addArgument("--preview-url")
                .help("Specify the url for the link preview (the same url must also appear in the message body).");
        subparser.addArgument("--preview-title").help("Specify the title for the link preview (mandatory).");
        subparser.addArgument("--preview-description").help("Specify the description for the link preview (optional).");
        subparser.addArgument("--preview-image").help("Specify the image file for the link preview (optional).");
        subparser.addArgument("--story-timestamp")
                .type(long.class)
                .help("Specify the timestamp of a story to reply to.");
        subparser.addArgument("--story-author").help("Specify the number of the author of the story.");
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
                outputResult(outputWriter, results);
                return;
            } catch (IOException e) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
            }
        }

        final var stickerString = ns.getString("sticker");
        final var sticker = stickerString == null ? null : parseSticker(stickerString);

        var messageText = ns.getString("message");
        final var readMessageFromStdin = ns.getBoolean("message-from-stdin") == Boolean.TRUE;
        if (readMessageFromStdin) {
            logger.debug("Reading message from stdin...");
            try {
                messageText = IOUtils.readAll(System.in, IOUtils.getConsoleCharset());
            } catch (IOException e) {
                throw new UserErrorException("Failed to read message from stdin: " + e.getMessage());
            }
        } else if (messageText == null) {
            messageText = "";
        }

        List<String> attachments = ns.getList("attachment");
        if (attachments == null) {
            attachments = List.of();
        }

        List<String> mentionStrings = ns.getList("mention");
        final var mentions = mentionStrings == null ? List.<Message.Mention>of() : parseMentions(m, mentionStrings);

        final Message.Quote quote;
        final var quoteTimestamp = ns.getLong("quote-timestamp");
        if (quoteTimestamp != null) {
            final var quoteAuthor = ns.getString("quote-author");
            final var quoteMessage = ns.getString("quote-message");
            List<String> quoteMentionStrings = ns.getList("quote-mention");
            final var quoteMentions = quoteMentionStrings == null
                    ? List.<Message.Mention>of()
                    : parseMentions(m, quoteMentionStrings);
            quote = new Message.Quote(quoteTimestamp,
                    CommandUtil.getSingleRecipientIdentifier(quoteAuthor, m.getSelfNumber()),
                    quoteMessage == null ? "" : quoteMessage,
                    quoteMentions);
        } else {
            quote = null;
        }

        final List<Message.Preview> previews;
        String previewUrl = ns.getString("preview-url");
        if (previewUrl != null) {
            String previewTitle = ns.getString("preview-title");
            String previewDescription = ns.getString("preview-description");
            String previewImage = ns.getString("preview-image");
            previews = List.of(new Message.Preview(previewUrl,
                    Optional.ofNullable(previewTitle).orElse(""),
                    Optional.ofNullable(previewDescription).orElse(""),
                    Optional.ofNullable(previewImage)));
        } else {
            previews = List.of();
        }

        final Message.StoryReply storyReply;
        final var storyReplyTimestamp = ns.getLong("story-timestamp");
        if (storyReplyTimestamp != null) {
            final var storyAuthor = ns.getString("story-author");
            storyReply = new Message.StoryReply(storyReplyTimestamp,
                    CommandUtil.getSingleRecipientIdentifier(storyAuthor, m.getSelfNumber()));
        } else {
            storyReply = null;
        }

        if (messageText.isEmpty() && attachments.isEmpty() && sticker == null && quote == null) {
            throw new UserErrorException(
                    "Sending empty message is not allowed, either a message, attachment or sticker must be given.");
        }

        try {
            final var message = new Message(messageText,
                    attachments,
                    mentions,
                    Optional.ofNullable(quote),
                    Optional.ofNullable(sticker),
                    previews,
                    Optional.ofNullable((storyReply)));
            var results = m.sendMessage(message, recipientIdentifiers);
            outputResult(outputWriter, results);
        } catch (AttachmentInvalidException | IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        } catch (InvalidStickerException e) {
            throw new UserErrorException("Failed to send sticker: " + e.getMessage(), e);
        }
    }

    private List<Message.Mention> parseMentions(
            final Manager m, final List<String> mentionStrings
    ) throws UserErrorException {
        List<Message.Mention> mentions;
        final Pattern mentionPattern = Pattern.compile("(\\d+):(\\d+):(.+)");
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
        return mentions;
    }

    private Message.Sticker parseSticker(final String stickerString) throws UserErrorException {
        final Pattern stickerPattern = Pattern.compile("([\\da-f]+):(\\d+)");
        final var matcher = stickerPattern.matcher(stickerString);
        if (!matcher.matches() || matcher.group(1).length() % 2 != 0) {
            throw new UserErrorException("Invalid sticker syntax ("
                    + stickerString
                    + ") expected 'stickerPackId:stickerId'");
        }
        return new Message.Sticker(Hex.toByteArray(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
}
