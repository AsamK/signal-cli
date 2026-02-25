package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TextStyle;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
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

    private static final Logger logger = LoggerFactory.getLogger(SendCommand.class);

    @Override
    public String getName() {
        return "send";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a message to another user or group.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("-u", "--username").help("Specify the recipient username or username link.").nargs("*");
        subparser.addArgument("--note-to-self").help("Send the message to self.").action(Arguments.storeTrue());
        subparser.addArgument("--notify-self")
                .help("If self is part of recipients/groups send a normal message, not a sync message.")
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
        subparser.addArgument("--view-once")
                .action(Arguments.storeTrue())
                .help("Send the message as a view once message");
        subparser.addArgument("-e", "--end-session", "--endsession")
                .help("Clear session state and send end session message.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--mention")
                .nargs("*")
                .help("Mention another group member (syntax: start:length:recipientNumber). "
                        + "Unit of start and length is UTF-16 code units, NOT Unicode code points.");
        subparser.addArgument("--text-style")
                .nargs("*")
                .help("Style parts of the message text (syntax: start:length:STYLE). "
                        + "Unit of start and length is UTF-16 code units, NOT Unicode code points.");
        subparser.addArgument("--quote-timestamp")
                .type(long.class)
                .help("Specify the timestamp of a previous message with the recipient or group to add a quote to the new message.");
        subparser.addArgument("--quote-author").help("Specify the number of the author of the original message.");
        subparser.addArgument("--quote-message").help("Specify the message of the original message.");
        subparser.addArgument("--quote-mention")
                .nargs("*")
                .help("Quote with mention of another group member (syntax: start:length:recipientNumber)");
        subparser.addArgument("--quote-attachment")
                .nargs("*")
                .help("Specify the attachments of the original message (syntax: contentType[:filename[:previewFile]]), e.g. 'audio/aac' or 'image/png:test.png:/tmp/preview.jpg'.");
        subparser.addArgument("--quote-text-style")
                .nargs("*")
                .help("Quote with style parts of the message text (syntax: start:length:STYLE)");
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
        subparser.addArgument("--edit-timestamp")
                .type(long.class)
                .help("Specify the timestamp of a previous message with the recipient or group to send an edited message.");
        subparser.addArgument("--no-urgent")
                .action(Arguments.storeTrue())
                .help("Send the message without the urgent flag, so no push notification is triggered for the recipient. "
                        + "The message will still be delivered in real-time if the recipient's app is active.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var notifySelf = Boolean.TRUE.equals(ns.getBoolean("notify-self"));
        final var isNoteToSelf = Boolean.TRUE.equals(ns.getBoolean("note-to-self"));
        final var noUrgent = Boolean.TRUE.equals(ns.getBoolean("no-urgent"));
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");
        final var usernameStrings = ns.<String>getList("username");

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                isNoteToSelf,
                recipientStrings,
                groupIdStrings,
                usernameStrings);

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

        var attachments = ns.<String>getList("attachment");
        if (attachments == null) {
            attachments = List.of();
        }
        final var viewOnce = Boolean.TRUE.equals(ns.getBoolean("view-once"));

        final var selfNumber = m.getSelfNumber();

        final var mentionStrings = ns.<String>getList("mention");
        final var mentions = mentionStrings == null
                ? List.<Message.Mention>of()
                : parseMentions(selfNumber, mentionStrings);

        final var textStyleStrings = ns.<String>getList("text-style");
        final var textStyles = textStyleStrings == null ? List.<TextStyle>of() : parseTextStyles(textStyleStrings);

        final Message.Quote quote;
        final var quoteTimestamp = ns.getLong("quote-timestamp");
        if (quoteTimestamp != null) {
            final var quoteAuthor = ns.getString("quote-author");
            if (quoteAuthor == null) {
                throw new UserErrorException("Quote author parameter is missing");
            }
            final var quoteMessage = ns.getString("quote-message");
            final var quoteMentionStrings = ns.<String>getList("quote-mention");
            final var quoteMentions = quoteMentionStrings == null
                    ? List.<Message.Mention>of()
                    : parseMentions(selfNumber, quoteMentionStrings);
            final var quoteTextStyleStrings = ns.<String>getList("quote-text-style");
            final var quoteAttachmentStrings = ns.<String>getList("quote-attachment");
            final var quoteTextStyles = quoteTextStyleStrings == null
                    ? List.<TextStyle>of()
                    : parseTextStyles(quoteTextStyleStrings);
            final var quoteAttachments = quoteAttachmentStrings == null
                    ? List.<Message.Quote.Attachment>of()
                    : parseQuoteAttachments(quoteAttachmentStrings);
            quote = new Message.Quote(quoteTimestamp,
                    CommandUtil.getSingleRecipientIdentifier(quoteAuthor, selfNumber),
                    quoteMessage == null ? "" : quoteMessage,
                    quoteMentions,
                    quoteTextStyles,
                    quoteAttachments);
        } else {
            quote = null;
        }

        final List<Message.Preview> previews;
        final var previewUrl = ns.getString("preview-url");
        if (previewUrl != null) {
            final var previewTitle = ns.getString("preview-title");
            final var previewDescription = ns.getString("preview-description");
            final var previewImage = ns.getString("preview-image");
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
                    CommandUtil.getSingleRecipientIdentifier(storyAuthor, selfNumber));
        } else {
            storyReply = null;
        }

        if (messageText.isEmpty() && attachments.isEmpty() && sticker == null && quote == null) {
            throw new UserErrorException(
                    "Sending empty message is not allowed, either a message, attachment or sticker must be given.");
        }

        final var editTimestamp = ns.getLong("edit-timestamp");

        try {
            final var message = new Message(messageText,
                    attachments,
                    viewOnce,
                    mentions,
                    Optional.ofNullable(quote),
                    Optional.ofNullable(sticker),
                    previews,
                    Optional.ofNullable((storyReply)),
                    textStyles,
                    noUrgent);
            var results = editTimestamp != null
                    ? m.sendEditMessage(message, recipientIdentifiers, editTimestamp)
                    : m.sendMessage(message, recipientIdentifiers, notifySelf);
            outputResult(outputWriter, results);
        } catch (AttachmentInvalidException | IOException e) {
            if (e instanceof IOException io && io.getMessage().contains("No prekeys available")) {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + "), maybe one of the devices of the recipient wasn't online for a while.",
                        e);
            } else {
                throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                        .getSimpleName() + ")", e);
            }
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new UserErrorException(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        } catch (InvalidStickerException e) {
            throw new UserErrorException("Failed to send sticker: " + e.getMessage(), e);
        }
    }

    private List<Message.Mention> parseMentions(
            final String selfNumber,
            final List<String> mentionStrings
    ) throws UserErrorException {
        final var mentionPattern = Pattern.compile("(\\d+):(\\d+):(.+)");
        final var mentions = new ArrayList<Message.Mention>();
        for (final var mention : mentionStrings) {
            final var matcher = mentionPattern.matcher(mention);
            if (!matcher.matches()) {
                throw new UserErrorException("Invalid mention syntax ("
                        + mention
                        + ") expected 'start:length:recipientNumber'");
            }
            mentions.add(new Message.Mention(CommandUtil.getSingleRecipientIdentifier(matcher.group(3), selfNumber),
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))));
        }
        return mentions;
    }

    private List<TextStyle> parseTextStyles(
            final List<String> textStylesStrings
    ) throws UserErrorException {
        final var textStylePattern = Pattern.compile("(\\d+):(\\d+):(.+)");
        final var textStyles = new ArrayList<TextStyle>();
        for (final var textStyle : textStylesStrings) {
            final var matcher = textStylePattern.matcher(textStyle);
            if (!matcher.matches()) {
                throw new UserErrorException("Invalid textStyle syntax ("
                        + textStyle
                        + ") expected 'start:length:STYLE'");
            }
            final var style = TextStyle.Style.from(matcher.group(3));
            if (style == null) {
                throw new UserErrorException("Invalid style: " + matcher.group(3));
            }
            textStyles.add(new TextStyle(style,
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))));
        }
        return textStyles;
    }

    private Message.Sticker parseSticker(final String stickerString) throws UserErrorException {
        final var stickerPattern = Pattern.compile("([\\da-f]+):(\\d+)");
        final var matcher = stickerPattern.matcher(stickerString);
        if (!matcher.matches() || matcher.group(1).length() % 2 != 0) {
            throw new UserErrorException("Invalid sticker syntax ("
                    + stickerString
                    + ") expected 'stickerPackId:stickerId'");
        }
        return new Message.Sticker(Hex.toByteArray(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private List<Message.Quote.Attachment> parseQuoteAttachments(
            final List<String> attachmentStrings
    ) throws UserErrorException {
        final var attachmentPattern = Pattern.compile("([^:]+)(:([^:]+)(:(.+))?)?");
        final var attachments = new ArrayList<Message.Quote.Attachment>();
        for (final var attachment : attachmentStrings) {
            final var matcher = attachmentPattern.matcher(attachment);
            if (!matcher.matches()) {
                throw new UserErrorException("Invalid attachment syntax ("
                        + attachment
                        + ") expected 'contentType[:filename[:previewFile]]'");
            }
            attachments.add(new Message.Quote.Attachment(matcher.group(1), matcher.group(3), matcher.group(5)));
        }
        return attachments;
    }
}
