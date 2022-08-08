package org.asamk.signal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.UntrustedIdentityException;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Hex;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    final Manager m;
    final PlainTextWriter writer;

    public ReceiveMessageHandler(Manager m, final PlainTextWriter writer) {
        this.m = m;
        this.writer = writer;
    }

    @Override
    public void handleMessage(MessageEnvelope envelope, Throwable exception) {
        synchronized (writer) {
            handleMessageInternal(envelope, exception);
        }
    }

    private void handleMessageInternal(MessageEnvelope envelope, Throwable exception) {
        var source = envelope.sourceAddress();
        writer.println("Envelope from: {} (device: {})",
                source.map(this::formatContact).orElse("unknown source"),
                envelope.sourceDevice());
        writer.println("Timestamp: {}", DateUtils.formatTimestamp(envelope.timestamp()));
        writer.println("Server timestamps: received: {} delivered: {}",
                DateUtils.formatTimestamp(envelope.serverReceivedTimestamp()),
                DateUtils.formatTimestamp(envelope.serverDeliveredTimestamp()));
        if (envelope.isUnidentifiedSender()) {
            writer.println("Sent by unidentified/sealed sender");
        }

        if (exception != null) {
            if (exception instanceof UntrustedIdentityException e) {
                writer.println(
                        "The user’s key is untrusted, either the user has reinstalled Signal or a third party sent this message.");
                final var recipientName = e.getSender().getLegacyIdentifier();
                writer.println(
                        "Use 'signal-cli -a {} listIdentities -n {}', verify the key and run 'signal-cli -a {} trust -v \"FINGER_PRINT\" {}' to mark it as trusted",
                        m.getSelfNumber(),
                        recipientName,
                        m.getSelfNumber(),
                        recipientName);
                writer.println(
                        "If you don't care about security, use 'signal-cli -a {} trust -a {}' to trust it without verification",
                        m.getSelfNumber(),
                        recipientName);
            } else {
                writer.println("Exception: {} ({})", exception.getMessage(), exception.getClass().getSimpleName());
            }
        }

        if (envelope.data().isPresent()) {
            var message = envelope.data().get();
            printDataMessage(writer, message);
        }
        if (envelope.story().isPresent()) {
            var message = envelope.story().get();
            printStoryMessage(writer.indentedWriter(), message);
        }
        if (envelope.sync().isPresent()) {
            writer.println("Received a sync message");
            var syncMessage = envelope.sync().get();
            printSyncMessage(writer, syncMessage);
        }
        if (envelope.call().isPresent()) {
            writer.println("Received a call message");
            var callMessage = envelope.call().get();
            printCallMessage(writer.indentedWriter(), callMessage);
        }
        if (envelope.receipt().isPresent()) {
            writer.println("Received a receipt message");
            var receiptMessage = envelope.receipt().get();
            printReceiptMessage(writer.indentedWriter(), receiptMessage);
        }
        if (envelope.typing().isPresent()) {
            writer.println("Received a typing message");
            var typingMessage = envelope.typing().get();
            printTypingMessage(writer.indentedWriter(), typingMessage);
        }
        writer.println();
    }

    private void printDataMessage(
            PlainTextWriter writer, MessageEnvelope.Data message
    ) {
        writer.println("Message timestamp: {}", DateUtils.formatTimestamp(message.timestamp()));
        if (message.isViewOnce()) {
            writer.println("=VIEW ONCE=");
        }

        if (message.body().isPresent()) {
            writer.println("Body: {}", message.body().get());
        }
        if (message.groupContext().isPresent()) {
            writer.println("Group info:");
            final var groupContext = message.groupContext().get();
            printGroupContext(writer.indentedWriter(), groupContext);
        }
        if (message.storyContext().isPresent()) {
            writer.println("Story reply:");
            final var storyContext = message.storyContext().get();
            printStoryContext(writer.indentedWriter(), storyContext);
        }
        if (message.groupCallUpdate().isPresent()) {
            writer.println("Group call update:");
            final var groupCallUpdate = message.groupCallUpdate().get();
            writer.indentedWriter().println("Era id: {}", groupCallUpdate.eraId());
        }
        if (message.previews().size() > 0) {
            writer.println("Previews:");
            final var previews = message.previews();
            for (var preview : previews) {
                writer.println("- Preview");
                printPreview(writer.indentedWriter(), preview);
            }
        }
        if (message.sharedContacts().size() > 0) {
            writer.println("Contacts:");
            for (var contact : message.sharedContacts()) {
                writer.println("- Contact:");
                printSharedContact(writer.indentedWriter(), contact);
            }
        }
        if (message.sticker().isPresent()) {
            final var sticker = message.sticker().get();
            writer.println("Sticker:");
            printSticker(writer.indentedWriter(), sticker);
        }
        if (message.isEndSession()) {
            writer.println("Is end session");
        }
        if (message.isExpirationUpdate()) {
            writer.println("Is Expiration update: true");
        }
        if (message.expiresInSeconds() > 0) {
            writer.println("Expires in: {} seconds", message.expiresInSeconds());
        }
        if (message.isProfileKeyUpdate()) {
            writer.println("Profile key update");
        }
        if (message.hasProfileKey()) {
            writer.println("With profile key");
        }
        if (message.reaction().isPresent()) {
            writer.println("Reaction:");
            final var reaction = message.reaction().get();
            printReaction(writer.indentedWriter(), reaction);
        }
        if (message.quote().isPresent()) {
            writer.println("Quote:");
            var quote = message.quote().get();
            printQuote(writer.indentedWriter(), quote);
        }
        if (message.remoteDeleteId().isPresent()) {
            final var remoteDelete = message.remoteDeleteId().get();
            writer.println("Remote delete message: timestamp = {}", remoteDelete);
        }
        if (message.mentions().size() > 0) {
            writer.println("Mentions:");
            for (var mention : message.mentions()) {
                printMention(writer, mention);
            }
        }
        if (message.attachments().size() > 0) {
            writer.println("Attachments:");
            for (var attachment : message.attachments()) {
                writer.println("- Attachment:");
                printAttachment(writer.indentedWriter(), attachment);
            }
        }
    }

    private void printStoryMessage(
            PlainTextWriter writer, MessageEnvelope.Story message
    ) {
        writer.println("Story: with replies: {}", message.allowsReplies());
        if (message.groupId().isPresent()) {
            writer.println("Group info:");
            printGroupInfo(writer.indentedWriter(), message.groupId().get());
        }
        if (message.textAttachment().isPresent()) {
            writer.println("Body: {}", message.textAttachment().get().text().orElse(""));

            if (message.textAttachment().get().preview().isPresent()) {
                writer.println("Preview:");
                printPreview(writer.indentedWriter(), message.textAttachment().get().preview().get());
            }
        }
        if (message.fileAttachment().isPresent()) {
            writer.println("Attachments:");
            printAttachment(writer.indentedWriter(), message.fileAttachment().get());
        }
    }

    private void printTypingMessage(
            final PlainTextWriter writer, final MessageEnvelope.Typing typingMessage
    ) {
        writer.println("Action: {}", typingMessage.type());
        writer.println("Timestamp: {}", DateUtils.formatTimestamp(typingMessage.timestamp()));
        if (typingMessage.groupId().isPresent()) {
            writer.println("Group Info:");
            final var groupId = typingMessage.groupId().get();
            printGroupInfo(writer.indentedWriter(), groupId);
        }
    }

    private void printReceiptMessage(
            final PlainTextWriter writer, final MessageEnvelope.Receipt receiptMessage
    ) {
        writer.println("When: {}", DateUtils.formatTimestamp(receiptMessage.when()));
        if (receiptMessage.type() == MessageEnvelope.Receipt.Type.DELIVERY) {
            writer.println("Is delivery receipt");
        }
        if (receiptMessage.type() == MessageEnvelope.Receipt.Type.READ) {
            writer.println("Is read receipt");
        }
        if (receiptMessage.type() == MessageEnvelope.Receipt.Type.VIEWED) {
            writer.println("Is viewed receipt");
        }
        writer.println("Timestamps:");
        for (long timestamp : receiptMessage.timestamps()) {
            writer.println("- {}", DateUtils.formatTimestamp(timestamp));
        }
    }

    private void printCallMessage(
            final PlainTextWriter writer, final MessageEnvelope.Call callMessage
    ) {
        if (callMessage.destinationDeviceId().isPresent()) {
            final var deviceId = callMessage.destinationDeviceId().get();
            writer.println("Destination device id: {}", deviceId);
        }
        if (callMessage.groupId().isPresent()) {
            final var groupId = callMessage.groupId().get();
            writer.println("Destination group id: {}", groupId);
        }
        if (callMessage.timestamp().isPresent()) {
            writer.println("Timestamp: {}", DateUtils.formatTimestamp(callMessage.timestamp().get()));
        }
        if (callMessage.answer().isPresent()) {
            var answerMessage = callMessage.answer().get();
            writer.println("Answer message: {}, sdp: {})", answerMessage.id(), answerMessage.sdp());
        }
        if (callMessage.busy().isPresent()) {
            var busyMessage = callMessage.busy().get();
            writer.println("Busy message: {}", busyMessage.id());
        }
        if (callMessage.hangup().isPresent()) {
            var hangupMessage = callMessage.hangup().get();
            writer.println("Hangup message: {}", hangupMessage.id());
        }
        if (callMessage.iceUpdate().size() > 0) {
            writer.println("Ice update messages:");
            var iceUpdateMessages = callMessage.iceUpdate();
            for (var iceUpdateMessage : iceUpdateMessages) {
                writer.println("- {}, sdp: {}", iceUpdateMessage.id(), iceUpdateMessage.sdp());
            }
        }
        if (callMessage.offer().isPresent()) {
            var offerMessage = callMessage.offer().get();
            writer.println("Offer message: {}, sdp: {}", offerMessage.id(), offerMessage.sdp());
        }
        if (callMessage.opaque().isPresent()) {
            final var opaqueMessage = callMessage.opaque().get();
            writer.println("Opaque message: size {}, urgency: {}",
                    opaqueMessage.opaque().length,
                    opaqueMessage.urgency().name());
        }
    }

    private void printSyncMessage(
            final PlainTextWriter writer, final MessageEnvelope.Sync syncMessage
    ) {
        if (syncMessage.contacts().isPresent()) {
            final var contactsMessage = syncMessage.contacts().get();
            var type = contactsMessage.isComplete() ? "complete" : "partial";
            writer.println("Received {} sync contacts:", type);
        }
        if (syncMessage.groups().isPresent()) {
            writer.println("Received sync groups.");
        }
        if (syncMessage.read().size() > 0) {
            writer.println("Received sync read messages list");
            for (var rm : syncMessage.read()) {
                writer.println("- From: {} Message timestamp: {}",
                        formatContact(rm.sender()),
                        DateUtils.formatTimestamp(rm.timestamp()));
            }
        }
        if (syncMessage.viewed().size() > 0) {
            writer.println("Received sync viewed messages list");
            for (var vm : syncMessage.viewed()) {
                writer.println("- From: {} Message timestamp: {}",
                        formatContact(vm.sender()),
                        DateUtils.formatTimestamp(vm.timestamp()));
            }
        }
        if (syncMessage.sent().isPresent()) {
            writer.println("Received sync sent message");
            final var sentTranscriptMessage = syncMessage.sent().get();
            String to;
            if (sentTranscriptMessage.destination().isPresent()) {
                to = formatContact(sentTranscriptMessage.destination().get());
            } else if (sentTranscriptMessage.recipients().size() > 0) {
                to = sentTranscriptMessage.recipients()
                        .stream()
                        .map(this::formatContact)
                        .collect(Collectors.joining(", "));
            } else {
                to = "<unknown>";
            }
            writer.indentedWriter().println("To: {}", to);
            writer.indentedWriter()
                    .println("Timestamp: {}", DateUtils.formatTimestamp(sentTranscriptMessage.timestamp()));
            if (sentTranscriptMessage.expirationStartTimestamp() > 0) {
                writer.indentedWriter()
                        .println("Expiration started at: {}",
                                DateUtils.formatTimestamp(sentTranscriptMessage.expirationStartTimestamp()));
            }
            if (sentTranscriptMessage.message().isPresent()) {
                var message = sentTranscriptMessage.message().get();
                printDataMessage(writer.indentedWriter(), message);
            }
            if (sentTranscriptMessage.story().isPresent()) {
                var message = sentTranscriptMessage.story().get();
                printStoryMessage(writer.indentedWriter(), message);
            }
        }
        if (syncMessage.blocked().isPresent()) {
            writer.println("Received sync message with block list");
            writer.println("Blocked:");
            final var blockedList = syncMessage.blocked().get();
            for (var address : blockedList.recipients()) {
                writer.println("- {}", address.getLegacyIdentifier());
            }
            for (var groupId : blockedList.groupIds()) {
                writer.println("- {}", groupId.toBase64());
            }
        }
        if (syncMessage.viewOnceOpen().isPresent()) {
            final var viewOnceOpenMessage = syncMessage.viewOnceOpen().get();
            writer.println("Received sync message with view once open message:");
            writer.indentedWriter().println("Sender: {}", formatContact(viewOnceOpenMessage.sender()));
            writer.indentedWriter()
                    .println("Timestamp: {}", DateUtils.formatTimestamp(viewOnceOpenMessage.timestamp()));
        }
        if (syncMessage.messageRequestResponse().isPresent()) {
            final var requestResponseMessage = syncMessage.messageRequestResponse().get();
            writer.println("Received message request response:");
            writer.indentedWriter().println("Type: {}", requestResponseMessage.type());
            if (requestResponseMessage.groupId().isPresent()) {
                writer.println("For group:");
                printGroupInfo(writer.indentedWriter(), requestResponseMessage.groupId().get());
            }
            if (requestResponseMessage.person().isPresent()) {
                writer.indentedWriter().println("For Person: {}", formatContact(requestResponseMessage.person().get()));
            }
        }
    }

    private void printPreview(
            final PlainTextWriter writer, final MessageEnvelope.Data.Preview preview
    ) {
        writer.println("Title: {}", preview.title());
        writer.println("Description: {}", preview.description());
        writer.println("Date: {}", DateUtils.formatTimestamp(preview.date()));
        writer.println("Url: {}", preview.url());
        if (preview.image().isPresent()) {
            writer.println("Image:");
            printAttachment(writer.indentedWriter(), preview.image().get());
        }
    }

    private void printSticker(
            final PlainTextWriter writer, final MessageEnvelope.Data.Sticker sticker
    ) {
        writer.println("Pack id: {}", Hex.toStringCondensed(sticker.packId().serialize()));
        writer.println("Sticker id: {}", sticker.stickerId());
    }

    private void printReaction(
            final PlainTextWriter writer, final MessageEnvelope.Data.Reaction reaction
    ) {
        writer.println("Emoji: {}", reaction.emoji());
        writer.println("Target author: {}", formatContact(reaction.targetAuthor()));
        writer.println("Target timestamp: {}", DateUtils.formatTimestamp(reaction.targetSentTimestamp()));
        writer.println("Is remove: {}", reaction.isRemove());
    }

    private void printQuote(
            final PlainTextWriter writer, final MessageEnvelope.Data.Quote quote
    ) {
        writer.println("Id: {}", quote.id());
        writer.println("Author: {}", formatContact(quote.author()));
        if (quote.text().isPresent()) {
            writer.println("Text: {}", quote.text().get());
        }
        if (quote.mentions() != null && quote.mentions().size() > 0) {
            writer.println("Mentions:");
            for (var mention : quote.mentions()) {
                printMention(writer, mention);
            }
        }
        if (quote.attachments().size() > 0) {
            writer.println("Attachments:");
            for (var attachment : quote.attachments()) {
                writer.println("- Attachment:");
                printAttachment(writer.indentedWriter(), attachment);
            }
        }
    }

    private void printSharedContact(final PlainTextWriter writer, final MessageEnvelope.Data.SharedContact contact) {
        writer.println("Name:");
        var name = contact.name();
        writer.indent(w -> {
            if (name.display().isPresent() && !name.display().get().isBlank()) {
                w.println("Display name: {}", name.display().get());
            }
            if (name.given().isPresent() && !name.given().get().isBlank()) {
                w.println("First name: {}", name.given().get());
            }
            if (name.middle().isPresent() && !name.middle().get().isBlank()) {
                w.println("Middle name: {}", name.middle().get());
            }
            if (name.family().isPresent() && !name.family().get().isBlank()) {
                w.println("Family name: {}", name.family().get());
            }
            if (name.prefix().isPresent() && !name.prefix().get().isBlank()) {
                w.println("Prefix name: {}", name.prefix().get());
            }
            if (name.suffix().isPresent() && !name.suffix().get().isBlank()) {
                w.println("Suffix name: {}", name.suffix().get());
            }
        });

        if (contact.avatar().isPresent()) {
            var avatar = contact.avatar().get();
            writer.println("Avatar: (profile: {})", avatar.isProfile());
            printAttachment(writer.indentedWriter(), avatar.attachment());
        }

        if (contact.organization().isPresent()) {
            writer.println("Organisation: {}", contact.organization().get());
        }

        if (contact.phone().size() > 0) {
            writer.println("Phone details:");
            for (var phone : contact.phone()) {
                writer.println("- Phone:");
                writer.indent(w -> {
                    w.println("Number: {}", phone.value());
                    w.println("Type: {}", phone.type());
                    if (phone.label().isPresent() && !phone.label().get().isBlank()) {
                        w.println("Label: {}", phone.label().get());
                    }
                });
            }
        }

        if (contact.email().size() > 0) {
            writer.println("Email details:");
            for (var email : contact.email()) {
                writer.println("- Email:");
                writer.indent(w -> {
                    w.println("Address: {}", email.value());
                    w.println("Type: {}", email.type());
                    if (email.label().isPresent() && !email.label().get().isBlank()) {
                        w.println("Label: {}", email.label().get());
                    }
                });
            }
        }

        if (contact.address().size() > 0) {
            writer.println("Address details:");
            for (var address : contact.address()) {
                writer.println("- Address:");
                writer.indent(w -> {
                    w.println("Type: {}", address.type());
                    if (address.label().isPresent() && !address.label().get().isBlank()) {
                        w.println("Label: {}", address.label().get());
                    }
                    if (address.street().isPresent() && !address.street().get().isBlank()) {
                        w.println("Street: {}", address.street().get());
                    }
                    if (address.pobox().isPresent() && !address.pobox().get().isBlank()) {
                        w.println("Pobox: {}", address.pobox().get());
                    }
                    if (address.neighborhood().isPresent() && !address.neighborhood().get().isBlank()) {
                        w.println("Neighbourhood: {}", address.neighborhood().get());
                    }
                    if (address.city().isPresent() && !address.city().get().isBlank()) {
                        w.println("City: {}", address.city().get());
                    }
                    if (address.region().isPresent() && !address.region().get().isBlank()) {
                        w.println("Region: {}", address.region().get());
                    }
                    if (address.postcode().isPresent() && !address.postcode().get().isBlank()) {
                        w.println("Postcode: {}", address.postcode().get());
                    }
                    if (address.country().isPresent() && !address.country().get().isBlank()) {
                        w.println("Country: {}", address.country().get());
                    }
                });
            }
        }
    }

    private void printGroupContext(
            final PlainTextWriter writer, final MessageEnvelope.Data.GroupContext groupContext
    ) {
        printGroupInfo(writer, groupContext.groupId());
        writer.println("Revision: {}", groupContext.revision());
        writer.println("Type: {}", groupContext.isGroupUpdate() ? "UPDATE" : "DELIVER");
    }

    private void printStoryContext(
            final PlainTextWriter writer, final MessageEnvelope.Data.StoryContext storyContext
    ) {
        writer.println("Sender: {}", formatContact(storyContext.author()));
        writer.println("Sent timestamp: {}", storyContext.sentTimestamp());
    }

    private void printGroupInfo(final PlainTextWriter writer, final GroupId groupId) {
        writer.println("Id: {}", groupId.toBase64());

        var group = m.getGroup(groupId);
        if (group != null) {
            writer.println("Name: {}", group.title());
        } else {
            writer.println("Name: <Unknown group>");
        }
    }

    private void printMention(
            PlainTextWriter writer, MessageEnvelope.Data.Mention mention
    ) {
        writer.println("- {}: {} (length: {})", formatContact(mention.recipient()), mention.start(), mention.length());
    }

    private void printAttachment(PlainTextWriter writer, MessageEnvelope.Data.Attachment attachment) {
        writer.println("Content-Type: {}", attachment.contentType());
        writer.println("Type: {}", attachment.id().isPresent() ? "Pointer" : "Stream");
        if (attachment.id().isPresent()) {
            writer.println("Id: {}", attachment.id().get());
        }
        if (attachment.uploadTimestamp().isPresent()) {
            writer.println("Upload timestamp: {}", DateUtils.formatTimestamp(attachment.uploadTimestamp().get()));
        }
        if (attachment.caption().isPresent()) {
            writer.println("Caption: {}", attachment.caption().get());
        }
        if (attachment.fileName().isPresent()) {
            writer.println("Filename: {}", attachment.fileName().get());
        }
        if (attachment.size().isPresent() || attachment.preview().isPresent()) {
            writer.println("Size: {}{}",
                    attachment.size().isPresent() ? attachment.size().get() + " bytes" : "<unavailable>",
                    attachment.preview().isPresent() ? " (Preview is available: "
                            + attachment.preview().get().length
                            + " bytes)" : "");
        }
        if (attachment.thumbnail().isPresent()) {
            writer.println("Thumbnail:");
            printAttachment(writer.indentedWriter(), attachment.thumbnail().get());
        }
        final var flags = new ArrayList<String>();
        if (attachment.isVoiceNote()) {
            flags.add("voice note");
        }
        if (attachment.isBorderless()) {
            flags.add("borderless");
        }
        if (attachment.isGif()) {
            flags.add("video gif");
        }
        if (flags.size() > 0) {
            writer.println("Flags: {}", String.join(", ", flags));
        }
        if (attachment.width().isPresent() || attachment.height().isPresent()) {
            writer.println("Dimensions: {}x{}", attachment.width().orElse(0), attachment.height().orElse(0));
        }
        if (attachment.file().isPresent()) {
            var file = attachment.file().get();
            if (file.exists()) {
                writer.println("Stored plaintext in: {}", file);
            }
        }
    }

    private String formatContact(RecipientAddress address) {
        final var number = address.getLegacyIdentifier();
        final var name = m.getContactOrProfileName(RecipientIdentifier.Single.fromAddress(address));
        if (name == null || name.isEmpty()) {
            return number;
        } else {
            return MessageFormatter.arrayFormat("“{}” {}", new Object[]{name, number}).getMessage();
        }
    }
}
