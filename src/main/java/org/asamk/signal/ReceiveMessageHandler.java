package org.asamk.signal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Util;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.slf4j.helpers.MessageFormatter;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class ReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    final Manager m;

    public ReceiveMessageHandler(Manager m) {
        this.m = m;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        PlainTextWriter writer = new PlainTextWriterImpl(System.out);

        if (envelope.hasSource()) {
            var source = envelope.getSourceAddress();
            writer.println("Envelope from: {} (device: {})", formatContact(source), envelope.getSourceDevice());
            if (source.getRelay().isPresent()) {
                writer.println("Relayed by: {}", source.getRelay().get());
            }
        } else {
            writer.println("Envelope from: unknown source");
        }
        writer.println("Timestamp: {}", DateUtils.formatTimestamp(envelope.getTimestamp()));
        if (envelope.isUnidentifiedSender()) {
            writer.println("Sent by unidentified/sealed sender");
        }

        if (envelope.isReceipt()) {
            writer.println("Got receipt.");
        } else if (envelope.isSignalMessage() || envelope.isPreKeySignalMessage() || envelope.isUnidentifiedSender()) {
            if (exception != null) {
                if (exception instanceof ProtocolUntrustedIdentityException) {
                    var e = (ProtocolUntrustedIdentityException) exception;
                    writer.println(
                            "The user’s key is untrusted, either the user has reinstalled Signal or a third party sent this message.");
                    final var recipientName = getLegacyIdentifier(m.resolveSignalServiceAddress(e.getSender()));
                    writer.println(
                            "Use 'signal-cli -u {} listIdentities -n {}', verify the key and run 'signal-cli -u {} trust -v \"FINGER_PRINT\" {}' to mark it as trusted",
                            m.getUsername(),
                            recipientName,
                            m.getUsername(),
                            recipientName);
                    writer.println(
                            "If you don't care about security, use 'signal-cli -u {} trust -a {}' to trust it without verification",
                            m.getUsername(),
                            recipientName);
                } else {
                    writer.println("Exception: {} ({})", exception.getMessage(), exception.getClass().getSimpleName());
                }
            }
            if (content == null) {
                writer.println("No message content");
            } else {
                writer.println("Sender: {} (device: {})",
                        formatContact(content.getSender()),
                        content.getSenderDevice());
                writer.println("Server timestamps: received: {} delivered: {}",
                        DateUtils.formatTimestamp(content.getServerReceivedTimestamp()),
                        DateUtils.formatTimestamp(content.getServerDeliveredTimestamp()));

                if (content.getDataMessage().isPresent()) {
                    var message = content.getDataMessage().get();
                    printDataMessage(writer, message);
                }
                if (content.getSyncMessage().isPresent()) {
                    writer.println("Received a sync message");
                    var syncMessage = content.getSyncMessage().get();
                    printSyncMessage(writer, syncMessage);
                }

                if (content.getCallMessage().isPresent()) {
                    writer.println("Received a call message");
                    var callMessage = content.getCallMessage().get();
                    printCallMessage(writer.indentedWriter(), callMessage);
                }
                if (content.getReceiptMessage().isPresent()) {
                    writer.println("Received a receipt message");
                    var receiptMessage = content.getReceiptMessage().get();
                    printReceiptMessage(writer.indentedWriter(), receiptMessage);
                }
                if (content.getTypingMessage().isPresent()) {
                    writer.println("Received a typing message");
                    var typingMessage = content.getTypingMessage().get();
                    printTypingMessage(writer.indentedWriter(), typingMessage);
                }
            }
        } else {
            writer.println("Unknown message received.");
        }
        writer.println();
    }

    private void printDataMessage(
            PlainTextWriter writer, SignalServiceDataMessage message
    ) {
        writer.println("Message timestamp: {}", DateUtils.formatTimestamp(message.getTimestamp()));
        if (message.isViewOnce()) {
            writer.println("=VIEW ONCE=");
        }

        if (message.getBody().isPresent()) {
            writer.println("Body: {}", message.getBody().get());
        }
        if (message.getGroupContext().isPresent()) {
            writer.println("Group info:");
            final var groupContext = message.getGroupContext().get();
            printGroupContext(writer.indentedWriter(), groupContext);
        }
        if (message.getGroupCallUpdate().isPresent()) {
            writer.println("Group call update:");
            final var groupCallUpdate = message.getGroupCallUpdate().get();
            writer.indentedWriter().println("Era id: {}", groupCallUpdate.getEraId());
        }
        if (message.getPreviews().isPresent()) {
            writer.println("Previews:");
            final var previews = message.getPreviews().get();
            for (var preview : previews) {
                writer.println("- Preview");
                printPreview(writer.indentedWriter(), preview);
            }
        }
        if (message.getSharedContacts().isPresent()) {
            final var sharedContacts = message.getSharedContacts().get();
            writer.println("Contacts:");
            for (var contact : sharedContacts) {
                writer.println("- Contact:");
                printSharedContact(writer.indentedWriter(), contact);
            }
        }
        if (message.getSticker().isPresent()) {
            final var sticker = message.getSticker().get();
            writer.println("Sticker:");
            printSticker(writer.indentedWriter(), sticker);
        }
        if (message.isEndSession()) {
            writer.println("Is end session");
        }
        if (message.isExpirationUpdate()) {
            writer.println("Is Expiration update: {}", message.isExpirationUpdate());
        }
        if (message.getExpiresInSeconds() > 0) {
            writer.println("Expires in: {} seconds", message.getExpiresInSeconds());
        }
        if (message.getProfileKey().isPresent()) {
            writer.println("Profile key update, key length: {}", message.getProfileKey().get().length);
        }
        if (message.getReaction().isPresent()) {
            writer.println("Reaction:");
            final var reaction = message.getReaction().get();
            printReaction(writer.indentedWriter(), reaction);
        }
        if (message.getQuote().isPresent()) {
            writer.println("Quote:");
            var quote = message.getQuote().get();
            printQuote(writer.indentedWriter(), quote);
        }
        if (message.getRemoteDelete().isPresent()) {
            final var remoteDelete = message.getRemoteDelete().get();
            writer.println("Remote delete message: timestamp = {}", remoteDelete.getTargetSentTimestamp());
        }
        if (message.getMentions().isPresent()) {
            writer.println("Mentions:");
            for (var mention : message.getMentions().get()) {
                printMention(writer, mention);
            }
        }
        if (message.getAttachments().isPresent()) {
            writer.println("Attachments:");
            for (var attachment : message.getAttachments().get()) {
                writer.println("- Attachment:");
                printAttachment(writer.indentedWriter(), attachment);
            }
        }
    }

    private void printTypingMessage(
            final PlainTextWriter writer, final SignalServiceTypingMessage typingMessage
    ) {
        writer.println("Action: {}", typingMessage.getAction());
        writer.println("Timestamp: {}", DateUtils.formatTimestamp(typingMessage.getTimestamp()));
        if (typingMessage.getGroupId().isPresent()) {
            writer.println("Group Info:");
            final var groupId = GroupId.unknownVersion(typingMessage.getGroupId().get());
            printGroupInfo(writer.indentedWriter(), groupId);
        }
    }

    private void printReceiptMessage(
            final PlainTextWriter writer, final SignalServiceReceiptMessage receiptMessage
    ) {
        writer.println("When: {}", DateUtils.formatTimestamp(receiptMessage.getWhen()));
        if (receiptMessage.isDeliveryReceipt()) {
            writer.println("Is delivery receipt");
        }
        if (receiptMessage.isReadReceipt()) {
            writer.println("Is read receipt");
        }
        if (receiptMessage.isViewedReceipt()) {
            writer.println("Is viewed receipt");
        }
        writer.println("Timestamps:");
        for (long timestamp : receiptMessage.getTimestamps()) {
            writer.println("- {}", DateUtils.formatTimestamp(timestamp));
        }
    }

    private void printCallMessage(
            final PlainTextWriter writer, final SignalServiceCallMessage callMessage
    ) {
        if (callMessage.getDestinationDeviceId().isPresent()) {
            final var deviceId = callMessage.getDestinationDeviceId().get();
            writer.println("Destination device id: {}", deviceId);
        }
        if (callMessage.getAnswerMessage().isPresent()) {
            var answerMessage = callMessage.getAnswerMessage().get();
            writer.println("Answer message: {}, sdp: {})", answerMessage.getId(), answerMessage.getSdp());
        }
        if (callMessage.getBusyMessage().isPresent()) {
            var busyMessage = callMessage.getBusyMessage().get();
            writer.println("Busy message: {}", busyMessage.getId());
        }
        if (callMessage.getHangupMessage().isPresent()) {
            var hangupMessage = callMessage.getHangupMessage().get();
            writer.println("Hangup message: {}", hangupMessage.getId());
        }
        if (callMessage.getIceUpdateMessages().isPresent()) {
            writer.println("Ice update messages:");
            var iceUpdateMessages = callMessage.getIceUpdateMessages().get();
            for (var iceUpdateMessage : iceUpdateMessages) {
                writer.println("- {}, sdp: {}", iceUpdateMessage.getId(), iceUpdateMessage.getSdp());
            }
        }
        if (callMessage.getOfferMessage().isPresent()) {
            var offerMessage = callMessage.getOfferMessage().get();
            writer.println("Offer message: {}, sdp: {}", offerMessage.getId(), offerMessage.getSdp());
        }
        if (callMessage.getOpaqueMessage().isPresent()) {
            final var opaqueMessage = callMessage.getOpaqueMessage().get();
            writer.println("Opaque message: size {}", opaqueMessage.getOpaque().length);
        }
    }

    private void printSyncMessage(
            final PlainTextWriter writer, final SignalServiceSyncMessage syncMessage
    ) {
        if (syncMessage.getContacts().isPresent()) {
            final var contactsMessage = syncMessage.getContacts().get();
            var type = contactsMessage.isComplete() ? "complete" : "partial";
            writer.println("Received {} sync contacts:", type);
            printAttachment(writer.indentedWriter(), contactsMessage.getContactsStream());
        }
        if (syncMessage.getGroups().isPresent()) {
            writer.println("Received sync groups:");
            printAttachment(writer.indentedWriter(), syncMessage.getGroups().get());
        }
        if (syncMessage.getRead().isPresent()) {
            writer.println("Received sync read messages list");
            for (var rm : syncMessage.getRead().get()) {
                writer.println("- From: {} Message timestamp: {}",
                        formatContact(rm.getSender()),
                        DateUtils.formatTimestamp(rm.getTimestamp()));
            }
        }
        if (syncMessage.getViewed().isPresent()) {
            writer.println("Received sync viewed messages list");
            for (var vm : syncMessage.getViewed().get()) {
                writer.println("- From: {} Message timestamp: {}",
                        formatContact(vm.getSender()),
                        DateUtils.formatTimestamp(vm.getTimestamp()));
            }
        }
        if (syncMessage.getRequest().isPresent()) {
            String type;
            if (syncMessage.getRequest().get().isContactsRequest()) {
                type = "contacts";
            } else if (syncMessage.getRequest().get().isGroupsRequest()) {
                type = "groups";
            } else if (syncMessage.getRequest().get().isBlockedListRequest()) {
                type = "blocked list";
            } else if (syncMessage.getRequest().get().isConfigurationRequest()) {
                type = "configuration";
            } else if (syncMessage.getRequest().get().isKeysRequest()) {
                type = "keys";
            } else {
                type = "<unknown>";
            }
            writer.println("Received sync request for: {}", type);
        }
        if (syncMessage.getSent().isPresent()) {
            writer.println("Received sync sent message");
            final var sentTranscriptMessage = syncMessage.getSent().get();
            String to;
            if (sentTranscriptMessage.getDestination().isPresent()) {
                to = formatContact(sentTranscriptMessage.getDestination().get());
            } else if (sentTranscriptMessage.getRecipients().size() > 0) {
                to = sentTranscriptMessage.getRecipients()
                        .stream()
                        .map(this::formatContact)
                        .collect(Collectors.joining(", "));
            } else {
                to = "<unknown>";
            }
            writer.indentedWriter().println("To: {}", to);
            writer.indentedWriter()
                    .println("Timestamp: {}", DateUtils.formatTimestamp(sentTranscriptMessage.getTimestamp()));
            if (sentTranscriptMessage.getExpirationStartTimestamp() > 0) {
                writer.indentedWriter()
                        .println("Expiration started at: {}",
                                DateUtils.formatTimestamp(sentTranscriptMessage.getExpirationStartTimestamp()));
            }
            var message = sentTranscriptMessage.getMessage();
            printDataMessage(writer.indentedWriter(), message);
        }
        if (syncMessage.getBlockedList().isPresent()) {
            writer.println("Received sync message with block list");
            writer.println("Blocked numbers:");
            final var blockedList = syncMessage.getBlockedList().get();
            for (var address : blockedList.getAddresses()) {
                writer.println("- {}", getLegacyIdentifier(address));
            }
        }
        if (syncMessage.getVerified().isPresent()) {
            writer.println("Received sync message with verified identities:");
            final var verifiedMessage = syncMessage.getVerified().get();
            writer.println("- {}: {}", formatContact(verifiedMessage.getDestination()), verifiedMessage.getVerified());
            var safetyNumber = Util.formatSafetyNumber(m.computeSafetyNumber(verifiedMessage.getDestination(),
                    verifiedMessage.getIdentityKey()));
            writer.indentedWriter().println(safetyNumber);
        }
        if (syncMessage.getConfiguration().isPresent()) {
            writer.println("Received sync message with configuration:");
            final var configurationMessage = syncMessage.getConfiguration().get();
            if (configurationMessage.getReadReceipts().isPresent()) {
                writer.println("- Read receipts: {}",
                        configurationMessage.getReadReceipts().get() ? "enabled" : "disabled");
            }
            if (configurationMessage.getLinkPreviews().isPresent()) {
                writer.println("- Link previews: {}",
                        configurationMessage.getLinkPreviews().get() ? "enabled" : "disabled");
            }
            if (configurationMessage.getTypingIndicators().isPresent()) {
                writer.println("- Typing indicators: {}",
                        configurationMessage.getTypingIndicators().get() ? "enabled" : "disabled");
            }
            if (configurationMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
                writer.println("- Unidentified Delivery Indicators: {}",
                        configurationMessage.getUnidentifiedDeliveryIndicators().get() ? "enabled" : "disabled");
            }
        }
        if (syncMessage.getFetchType().isPresent()) {
            final var fetchType = syncMessage.getFetchType().get();
            writer.println("Received sync message with fetch type: {}", fetchType);
        }
        if (syncMessage.getViewOnceOpen().isPresent()) {
            final var viewOnceOpenMessage = syncMessage.getViewOnceOpen().get();
            writer.println("Received sync message with view once open message:");
            writer.indentedWriter().println("Sender: {}", formatContact(viewOnceOpenMessage.getSender()));
            writer.indentedWriter()
                    .println("Timestamp: {}", DateUtils.formatTimestamp(viewOnceOpenMessage.getTimestamp()));
        }
        if (syncMessage.getStickerPackOperations().isPresent()) {
            final var stickerPackOperationMessages = syncMessage.getStickerPackOperations().get();
            writer.println("Received sync message with sticker pack operations:");
            for (var m : stickerPackOperationMessages) {
                writer.println("- {}", m.getType().isPresent() ? m.getType().get() : "<unknown>");
                if (m.getPackId().isPresent()) {
                    writer.indentedWriter()
                            .println("packId: {}", Base64.getEncoder().encodeToString(m.getPackId().get()));
                }
                if (m.getPackKey().isPresent()) {
                    writer.indentedWriter()
                            .println("packKey: {}", Base64.getEncoder().encodeToString(m.getPackKey().get()));
                }
            }
        }
        if (syncMessage.getMessageRequestResponse().isPresent()) {
            final var requestResponseMessage = syncMessage.getMessageRequestResponse().get();
            writer.println("Received message request response:");
            writer.indentedWriter().println("Type: {}", requestResponseMessage.getType());
            if (requestResponseMessage.getGroupId().isPresent()) {
                writer.println("For group:");
                printGroupInfo(writer.indentedWriter(),
                        GroupId.unknownVersion(requestResponseMessage.getGroupId().get()));
            }
            if (requestResponseMessage.getPerson().isPresent()) {
                writer.indentedWriter()
                        .println("For Person: {}", formatContact(requestResponseMessage.getPerson().get()));
            }
        }
        if (syncMessage.getKeys().isPresent()) {
            final var keysMessage = syncMessage.getKeys().get();
            writer.println("Received sync message with keys:");
            if (keysMessage.getStorageService().isPresent()) {
                writer.println("-  storage key: length: {}", keysMessage.getStorageService().get().serialize().length);
            }
        }
    }

    private void printPreview(
            final PlainTextWriter writer, final SignalServiceDataMessage.Preview preview
    ) {
        writer.println("Title: {}", preview.getTitle());
        writer.println("Description: {}", preview.getDescription());
        writer.println("Date: {}", DateUtils.formatTimestamp(preview.getDate()));
        writer.println("Url: {}", preview.getUrl());
        if (preview.getImage().isPresent()) {
            writer.println("Image:");
            printAttachment(writer.indentedWriter(), preview.getImage().get());
        }
    }

    private void printSticker(
            final PlainTextWriter writer, final SignalServiceDataMessage.Sticker sticker
    ) {
        writer.println("Pack id: {}", Base64.getEncoder().encodeToString(sticker.getPackId()));
        writer.println("Pack key: {}", Base64.getEncoder().encodeToString(sticker.getPackKey()));
        writer.println("Sticker id: {}", sticker.getStickerId());
        writer.println("Image:");
        printAttachment(writer.indentedWriter(), sticker.getAttachment());
    }

    private void printReaction(
            final PlainTextWriter writer, final SignalServiceDataMessage.Reaction reaction
    ) {
        writer.println("Emoji: {}", reaction.getEmoji());
        writer.println("Target author: {}", formatContact(m.resolveSignalServiceAddress(reaction.getTargetAuthor())));
        writer.println("Target timestamp: {}", DateUtils.formatTimestamp(reaction.getTargetSentTimestamp()));
        writer.println("Is remove: {}", reaction.isRemove());
    }

    private void printQuote(
            final PlainTextWriter writer, final SignalServiceDataMessage.Quote quote
    ) {
        writer.println("Id: {}", quote.getId());
        writer.println("Author: {}", getLegacyIdentifier(m.resolveSignalServiceAddress(quote.getAuthor())));
        writer.println("Text: {}", quote.getText());
        if (quote.getMentions() != null && quote.getMentions().size() > 0) {
            writer.println("Mentions:");
            for (var mention : quote.getMentions()) {
                printMention(writer, mention);
            }
        }
        if (quote.getAttachments().size() > 0) {
            writer.println("Attachments:");
            for (var attachment : quote.getAttachments()) {
                writer.println("- Filename: {}", attachment.getFileName());
                writer.indent(w -> {
                    w.println("Type: {}", attachment.getContentType());
                    w.println("Thumbnail:");
                    if (attachment.getThumbnail() != null) {
                        printAttachment(w, attachment.getThumbnail());
                    }
                });
            }
        }
    }

    private void printSharedContact(final PlainTextWriter writer, final SharedContact contact) {
        writer.println("Name:");
        var name = contact.getName();
        writer.indent(w -> {
            if (name.getDisplay().isPresent() && !name.getDisplay().get().isBlank()) {
                w.println("Display name: {}", name.getDisplay().get());
            }
            if (name.getGiven().isPresent() && !name.getGiven().get().isBlank()) {
                w.println("First name: {}", name.getGiven().get());
            }
            if (name.getMiddle().isPresent() && !name.getMiddle().get().isBlank()) {
                w.println("Middle name: {}", name.getMiddle().get());
            }
            if (name.getFamily().isPresent() && !name.getFamily().get().isBlank()) {
                w.println("Family name: {}", name.getFamily().get());
            }
            if (name.getPrefix().isPresent() && !name.getPrefix().get().isBlank()) {
                w.println("Prefix name: {}", name.getPrefix().get());
            }
            if (name.getSuffix().isPresent() && !name.getSuffix().get().isBlank()) {
                w.println("Suffix name: {}", name.getSuffix().get());
            }
        });

        if (contact.getAvatar().isPresent()) {
            var avatar = contact.getAvatar().get();
            writer.println("Avatar: (profile: {})", avatar.isProfile());
            printAttachment(writer.indentedWriter(), avatar.getAttachment());
        }

        if (contact.getOrganization().isPresent()) {
            writer.println("Organisation: {}", contact.getOrganization().get());
        }

        if (contact.getPhone().isPresent()) {
            writer.println("Phone details:");
            for (var phone : contact.getPhone().get()) {
                writer.println("- Phone:");
                writer.indent(w -> {
                    if (phone.getValue() != null) {
                        w.println("Number: {}", phone.getValue());
                    }
                    if (phone.getType() != null) {
                        w.println("Type: {}", phone.getType());
                    }
                    if (phone.getLabel().isPresent() && !phone.getLabel().get().isBlank()) {
                        w.println("Label: {}", phone.getLabel().get());
                    }
                });
            }
        }

        if (contact.getEmail().isPresent()) {
            writer.println("Email details:");
            for (var email : contact.getEmail().get()) {
                writer.println("- Email:");
                writer.indent(w -> {
                    if (email.getValue() != null) {
                        w.println("Address: {}", email.getValue());
                    }
                    if (email.getType() != null) {
                        w.println("Type: {}", email.getType());
                    }
                    if (email.getLabel().isPresent() && !email.getLabel().get().isBlank()) {
                        w.println("Label: {}", email.getLabel().get());
                    }
                });
            }
        }

        if (contact.getAddress().isPresent()) {
            writer.println("Address details:");
            for (var address : contact.getAddress().get()) {
                writer.println("- Address:");
                writer.indent(w -> {
                    if (address.getType() != null) {
                        w.println("Type: {}", address.getType());
                    }
                    if (address.getLabel().isPresent() && !address.getLabel().get().isBlank()) {
                        w.println("Label: {}", address.getLabel().get());
                    }
                    if (address.getStreet().isPresent() && !address.getStreet().get().isBlank()) {
                        w.println("Street: {}", address.getStreet().get());
                    }
                    if (address.getPobox().isPresent() && !address.getPobox().get().isBlank()) {
                        w.println("Pobox: {}", address.getPobox().get());
                    }
                    if (address.getNeighborhood().isPresent() && !address.getNeighborhood().get().isBlank()) {
                        w.println("Neighbourhood: {}", address.getNeighborhood().get());
                    }
                    if (address.getCity().isPresent() && !address.getCity().get().isBlank()) {
                        w.println("City: {}", address.getCity().get());
                    }
                    if (address.getRegion().isPresent() && !address.getRegion().get().isBlank()) {
                        w.println("Region: {}", address.getRegion().get());
                    }
                    if (address.getPostcode().isPresent() && !address.getPostcode().get().isBlank()) {
                        w.println("Postcode: {}", address.getPostcode().get());
                    }
                    if (address.getCountry().isPresent() && !address.getCountry().get().isBlank()) {
                        w.println("Country: {}", address.getCountry().get());
                    }
                });
            }
        }
    }

    private void printGroupContext(
            final PlainTextWriter writer, final SignalServiceGroupContext groupContext
    ) {
        final var groupId = GroupUtils.getGroupId(groupContext);
        if (groupContext.getGroupV1().isPresent()) {
            var groupInfo = groupContext.getGroupV1().get();
            printGroupInfo(writer, groupId);
            writer.println("Type: {}", groupInfo.getType());
            if (groupInfo.getMembers().isPresent()) {
                writer.println("Members:");
                for (var member : groupInfo.getMembers().get()) {
                    writer.println("- {}", formatContact(member));
                }
            }
            if (groupInfo.getAvatar().isPresent()) {
                writer.println("Avatar:");
                printAttachment(writer.indentedWriter(), groupInfo.getAvatar().get());
            }
        } else if (groupContext.getGroupV2().isPresent()) {
            final var groupInfo = groupContext.getGroupV2().get();
            printGroupInfo(writer, groupId);
            writer.println("Revision: {}", groupInfo.getRevision());
            writer.println("Master key length: {}", groupInfo.getMasterKey().serialize().length);
            writer.println("Has signed group change: {}", groupInfo.hasSignedGroupChange());
        }
    }

    private void printGroupInfo(final PlainTextWriter writer, final GroupId groupId) {
        writer.println("Id: {}", groupId.toBase64());

        var group = m.getGroup(groupId);
        if (group != null) {
            writer.println("Name: {}", group.getTitle());
        } else {
            writer.println("Name: <Unknown group>");
        }
    }

    private void printMention(
            PlainTextWriter writer, SignalServiceDataMessage.Mention mention
    ) {
        final var address = m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid(), null));
        writer.println("- {}: {} (length: {})", formatContact(address), mention.getStart(), mention.getLength());
    }

    private void printAttachment(PlainTextWriter writer, SignalServiceAttachment attachment) {
        writer.println("Content-Type: {}", attachment.getContentType());
        writer.println("Type: {}", attachment.isPointer() ? "Pointer" : attachment.isStream() ? "Stream" : "<unknown>");
        if (attachment.isPointer()) {
            final var pointer = attachment.asPointer();
            writer.println("Id: {} Key length: {}", pointer.getRemoteId(), pointer.getKey().length);
            if (pointer.getUploadTimestamp() > 0) {
                writer.println("Upload timestamp: {}", DateUtils.formatTimestamp(pointer.getUploadTimestamp()));
            }
            if (pointer.getCaption().isPresent()) {
                writer.println("Caption: {}", pointer.getCaption().get());
            }
            if (pointer.getFileName().isPresent()) {
                writer.println("Filename: {}", pointer.getFileName().get());
            }
            writer.println("Size: {}{}",
                    pointer.getSize().isPresent() ? pointer.getSize().get() + " bytes" : "<unavailable>",
                    pointer.getPreview().isPresent() ? " (Preview is available: "
                            + pointer.getPreview().get().length
                            + " bytes)" : "");
            final var flags = new ArrayList<String>();
            if (pointer.getVoiceNote()) {
                flags.add("voice note");
            }
            if (pointer.isBorderless()) {
                flags.add("borderless");
            }
            if (pointer.isGif()) {
                flags.add("video gif");
            }
            if (flags.size() > 0) {
                writer.println("Flags: {}", String.join(", ", flags));
            }
            if (pointer.getWidth() > 0 || pointer.getHeight() > 0) {
                writer.println("Dimensions: {}x{}", pointer.getWidth(), pointer.getHeight());
            }
            var file = m.getAttachmentFile(pointer.getRemoteId());
            if (file.exists()) {
                writer.println("Stored plaintext in: {}", file);
            }
        }
    }

    private String formatContact(SignalServiceAddress address) {
        final var number = getLegacyIdentifier(address);
        String name = null;
        try {
            name = m.getContactOrProfileName(number);
        } catch (InvalidNumberException ignored) {
        }
        if (name == null || name.isEmpty()) {
            return number;
        } else {
            return MessageFormatter.arrayFormat("“{}” {}", new Object[]{name, number}).getMessage();
        }
    }
}
