package org.asamk.signal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.File;
import java.util.Base64;
import java.util.List;

public class ReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    final Manager m;

    public ReceiveMessageHandler(Manager m) {
        this.m = m;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSource()) {
            SignalServiceAddress source = envelope.getSourceAddress();
            String name = m.getContactOrProfileName(source.getLegacyIdentifier());
            System.out.println(String.format("Envelope from: %s (device: %d)",
                    (name == null ? "" : "“" + name + "” ") + source.getLegacyIdentifier(),
                    envelope.getSourceDevice()));
            if (source.getRelay().isPresent()) {
                System.out.println("Relayed by: " + source.getRelay().get());
            }
        } else {
            System.out.println("Envelope from: unknown source");
        }
        System.out.println("Timestamp: " + DateUtils.formatTimestamp(envelope.getTimestamp()));
        if (envelope.isUnidentifiedSender()) {
            System.out.println("Sent by unidentified/sealed sender");
        }

        if (envelope.isReceipt()) {
            System.out.println("Got receipt.");
        } else if (envelope.isSignalMessage() || envelope.isPreKeySignalMessage() || envelope.isUnidentifiedSender()) {
            if (exception != null) {
                if (exception instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
                    org.whispersystems.libsignal.UntrustedIdentityException e = (org.whispersystems.libsignal.UntrustedIdentityException) exception;
                    System.out.println(
                            "The user’s key is untrusted, either the user has reinstalled Signal or a third party sent this message.");
                    System.out.println("Use 'signal-cli -u "
                            + m.getUsername()
                            + " listIdentities -n "
                            + e.getName()
                            + "', verify the key and run 'signal-cli -u "
                            + m.getUsername()
                            + " trust -v \"FINGER_PRINT\" "
                            + e.getName()
                            + "' to mark it as trusted");
                    System.out.println("If you don't care about security, use 'signal-cli -u "
                            + m.getUsername()
                            + " trust -a "
                            + e.getName()
                            + "' to trust it without verification");
                } else {
                    System.out.println("Exception: " + exception.getMessage() + " (" + exception.getClass()
                            .getSimpleName() + ")");
                }
            }
            if (content == null) {
                System.out.println("Failed to decrypt message.");
            } else {
                String senderName = m.getContactOrProfileName(content.getSender().getLegacyIdentifier());
                System.out.println(String.format("Sender: %s (device: %d)",
                        (senderName == null ? "" : "“" + senderName + "” ") + content.getSender().getLegacyIdentifier(),
                        content.getSenderDevice()));
                if (content.getDataMessage().isPresent()) {
                    SignalServiceDataMessage message = content.getDataMessage().get();
                    handleSignalServiceDataMessage(message);
                }
                if (content.getSyncMessage().isPresent()) {
                    System.out.println("Received a sync message");
                    SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

                    if (syncMessage.getContacts().isPresent()) {
                        final ContactsMessage contactsMessage = syncMessage.getContacts().get();
                        if (contactsMessage.isComplete()) {
                            System.out.println("Received complete sync contacts");
                        } else {
                            System.out.println("Received sync contacts");
                        }
                        printAttachment(contactsMessage.getContactsStream(), 0);
                    }
                    if (syncMessage.getGroups().isPresent()) {
                        System.out.println("Received sync groups");
                        printAttachment(syncMessage.getGroups().get(), 0);
                    }
                    if (syncMessage.getRead().isPresent()) {
                        System.out.println("Received sync read messages list");
                        for (ReadMessage rm : syncMessage.getRead().get()) {
                            String name = m.getContactOrProfileName(rm.getSender().getLegacyIdentifier());
                            System.out.println("From: "
                                    + (name == null ? "" : "“" + name + "” ")
                                    + rm.getSender()
                                    .getLegacyIdentifier()
                                    + " Message timestamp: "
                                    + DateUtils.formatTimestamp(rm.getTimestamp()));
                        }
                    }
                    if (syncMessage.getRequest().isPresent()) {
                        System.out.println("Received sync request");
                        if (syncMessage.getRequest().get().isContactsRequest()) {
                            System.out.println(" - contacts request");
                        }
                        if (syncMessage.getRequest().get().isGroupsRequest()) {
                            System.out.println(" - groups request");
                        }
                        if (syncMessage.getRequest().get().isBlockedListRequest()) {
                            System.out.println(" - blocked list request");
                        }
                        if (syncMessage.getRequest().get().isConfigurationRequest()) {
                            System.out.println(" - configuration request");
                        }
                        if (syncMessage.getRequest().get().isKeysRequest()) {
                            System.out.println(" - keys request");
                        }
                    }
                    if (syncMessage.getSent().isPresent()) {
                        System.out.println("Received sync sent message");
                        final SentTranscriptMessage sentTranscriptMessage = syncMessage.getSent().get();
                        String to;
                        if (sentTranscriptMessage.getDestination().isPresent()) {
                            String dest = sentTranscriptMessage.getDestination().get().getLegacyIdentifier();
                            String name = m.getContactOrProfileName(dest);
                            to = (name == null ? "" : "“" + name + "” ") + dest;
                        } else if (sentTranscriptMessage.getRecipients().size() > 0) {
                            StringBuilder toBuilder = new StringBuilder();
                            for (SignalServiceAddress dest : sentTranscriptMessage.getRecipients()) {
                                String name = m.getContactOrProfileName(dest.getLegacyIdentifier());
                                toBuilder.append(name == null ? "" : "“" + name + "” ")
                                        .append(dest.getLegacyIdentifier())
                                        .append(" ");
                            }
                            to = toBuilder.toString();
                        } else {
                            to = "Unknown";
                        }
                        System.out.println("To: " + to + " , Message timestamp: " + DateUtils.formatTimestamp(
                                sentTranscriptMessage.getTimestamp()));
                        if (sentTranscriptMessage.getExpirationStartTimestamp() > 0) {
                            System.out.println("Expiration started at: " + DateUtils.formatTimestamp(
                                    sentTranscriptMessage.getExpirationStartTimestamp()));
                        }
                        SignalServiceDataMessage message = sentTranscriptMessage.getMessage();
                        handleSignalServiceDataMessage(message);
                    }
                    if (syncMessage.getBlockedList().isPresent()) {
                        System.out.println("Received sync message with block list");
                        System.out.println("Blocked numbers:");
                        final BlockedListMessage blockedList = syncMessage.getBlockedList().get();
                        for (SignalServiceAddress address : blockedList.getAddresses()) {
                            System.out.println(" - " + address.getLegacyIdentifier());
                        }
                    }
                    if (syncMessage.getVerified().isPresent()) {
                        System.out.println("Received sync message with verified identities:");
                        final VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
                        System.out.println(" - "
                                + verifiedMessage.getDestination()
                                + ": "
                                + verifiedMessage.getVerified());
                        String safetyNumber = Util.formatSafetyNumber(m.computeSafetyNumber(verifiedMessage.getDestination(),
                                verifiedMessage.getIdentityKey()));
                        System.out.println("   " + safetyNumber);
                    }
                    if (syncMessage.getConfiguration().isPresent()) {
                        System.out.println("Received sync message with configuration:");
                        final ConfigurationMessage configurationMessage = syncMessage.getConfiguration().get();
                        if (configurationMessage.getReadReceipts().isPresent()) {
                            System.out.println(" - Read receipts: " + (
                                    configurationMessage.getReadReceipts().get() ? "enabled" : "disabled"
                            ));
                        }
                        if (configurationMessage.getLinkPreviews().isPresent()) {
                            System.out.println(" - Link previews: " + (
                                    configurationMessage.getLinkPreviews().get() ? "enabled" : "disabled"
                            ));
                        }
                        if (configurationMessage.getTypingIndicators().isPresent()) {
                            System.out.println(" - Typing indicators: " + (
                                    configurationMessage.getTypingIndicators().get() ? "enabled" : "disabled"
                            ));
                        }
                        if (configurationMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
                            System.out.println(" - Unidentified Delivery Indicators: " + (
                                    configurationMessage.getUnidentifiedDeliveryIndicators().get()
                                            ? "enabled"
                                            : "disabled"
                            ));
                        }
                    }
                    if (syncMessage.getFetchType().isPresent()) {
                        final SignalServiceSyncMessage.FetchType fetchType = syncMessage.getFetchType().get();
                        System.out.println("Received sync message with fetch type: " + fetchType.toString());
                    }
                    if (syncMessage.getViewOnceOpen().isPresent()) {
                        final ViewOnceOpenMessage viewOnceOpenMessage = syncMessage.getViewOnceOpen().get();
                        System.out.println("Received sync message with view once open message:");
                        System.out.println(" - Sender:" + viewOnceOpenMessage.getSender().getLegacyIdentifier());
                        System.out.println(" - Timestamp:" + viewOnceOpenMessage.getTimestamp());
                    }
                    if (syncMessage.getStickerPackOperations().isPresent()) {
                        final List<StickerPackOperationMessage> stickerPackOperationMessages = syncMessage.getStickerPackOperations()
                                .get();
                        System.out.println("Received sync message with sticker pack operations:");
                        for (StickerPackOperationMessage m : stickerPackOperationMessages) {
                            System.out.println(" - " + m.getType().toString());
                            if (m.getPackId().isPresent()) {
                                System.out.println("   packId: " + Base64.getEncoder()
                                        .encodeToString(m.getPackId().get()));
                            }
                            if (m.getPackKey().isPresent()) {
                                System.out.println("   packKey: " + Base64.getEncoder()
                                        .encodeToString(m.getPackKey().get()));
                            }
                        }
                    }
                    if (syncMessage.getMessageRequestResponse().isPresent()) {
                        final MessageRequestResponseMessage requestResponseMessage = syncMessage.getMessageRequestResponse()
                                .get();
                        System.out.println("Received message request response:");
                        System.out.println("  Type: " + requestResponseMessage.getType());
                        if (requestResponseMessage.getGroupId().isPresent()) {
                            System.out.println("  Group id: " + Base64.getEncoder()
                                    .encodeToString(requestResponseMessage.getGroupId().get()));
                        }
                        if (requestResponseMessage.getPerson().isPresent()) {
                            System.out.println("  Person: " + requestResponseMessage.getPerson()
                                    .get()
                                    .getLegacyIdentifier());
                        }
                    }
                    if (syncMessage.getKeys().isPresent()) {
                        final KeysMessage keysMessage = syncMessage.getKeys().get();
                        System.out.println("Received sync message with keys:");
                        if (keysMessage.getStorageService().isPresent()) {
                            System.out.println("  With storage key length: " + keysMessage.getStorageService()
                                    .get()
                                    .serialize().length);
                        } else {
                            System.out.println("  With empty storage key");
                        }
                    }
                }
                if (content.getCallMessage().isPresent()) {
                    System.out.println("Received a call message");
                    SignalServiceCallMessage callMessage = content.getCallMessage().get();
                    if (callMessage.getDestinationDeviceId().isPresent()) {
                        final Integer deviceId = callMessage.getDestinationDeviceId().get();
                        System.out.println("Destination device id: " + deviceId);
                    }
                    if (callMessage.getAnswerMessage().isPresent()) {
                        AnswerMessage answerMessage = callMessage.getAnswerMessage().get();
                        System.out.println("Answer message: " + answerMessage.getId() + ": " + answerMessage.getSdp());
                    }
                    if (callMessage.getBusyMessage().isPresent()) {
                        BusyMessage busyMessage = callMessage.getBusyMessage().get();
                        System.out.println("Busy message: " + busyMessage.getId());
                    }
                    if (callMessage.getHangupMessage().isPresent()) {
                        HangupMessage hangupMessage = callMessage.getHangupMessage().get();
                        System.out.println("Hangup message: " + hangupMessage.getId());
                    }
                    if (callMessage.getIceUpdateMessages().isPresent()) {
                        List<IceUpdateMessage> iceUpdateMessages = callMessage.getIceUpdateMessages().get();
                        for (IceUpdateMessage iceUpdateMessage : iceUpdateMessages) {
                            System.out.println("Ice update message: "
                                    + iceUpdateMessage.getId()
                                    + ", sdp: "
                                    + iceUpdateMessage.getSdp());
                        }
                    }
                    if (callMessage.getOfferMessage().isPresent()) {
                        OfferMessage offerMessage = callMessage.getOfferMessage().get();
                        System.out.println("Offer message: " + offerMessage.getId() + ": " + offerMessage.getSdp());
                    }
                    if (callMessage.getOpaqueMessage().isPresent()) {
                        final OpaqueMessage opaqueMessage = callMessage.getOpaqueMessage().get();
                        System.out.println("Opaque message: size " + opaqueMessage.getOpaque().length);
                    }
                }
                if (content.getReceiptMessage().isPresent()) {
                    System.out.println("Received a receipt message");
                    SignalServiceReceiptMessage receiptMessage = content.getReceiptMessage().get();
                    System.out.println(" - When: " + DateUtils.formatTimestamp(receiptMessage.getWhen()));
                    if (receiptMessage.isDeliveryReceipt()) {
                        System.out.println(" - Is delivery receipt");
                    }
                    if (receiptMessage.isReadReceipt()) {
                        System.out.println(" - Is read receipt");
                    }
                    if (receiptMessage.isViewedReceipt()) {
                        System.out.println(" - Is viewed receipt");
                    }
                    System.out.println(" - Timestamps:");
                    for (long timestamp : receiptMessage.getTimestamps()) {
                        System.out.println("    " + DateUtils.formatTimestamp(timestamp));
                    }
                }
                if (content.getTypingMessage().isPresent()) {
                    System.out.println("Received a typing message");
                    SignalServiceTypingMessage typingMessage = content.getTypingMessage().get();
                    System.out.println(" - Action: " + typingMessage.getAction());
                    System.out.println(" - Timestamp: " + DateUtils.formatTimestamp(typingMessage.getTimestamp()));
                    if (typingMessage.getGroupId().isPresent()) {
                        System.out.println(" - Group Info:");
                        final GroupId groupId = GroupId.unknownVersion(typingMessage.getGroupId().get());
                        System.out.println("   Id: " + groupId.toBase64());
                        GroupInfo group = m.getGroup(groupId);
                        if (group != null) {
                            System.out.println("   Name: " + group.getTitle());
                        } else {
                            System.out.println("   Name: <Unknown group>");
                        }
                    }
                }
            }
        } else {
            System.out.println("Unknown message received.");
        }
        System.out.println();
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message) {
        System.out.println("Message timestamp: " + DateUtils.formatTimestamp(message.getTimestamp()));
        if (message.isViewOnce()) {
            System.out.println("=VIEW ONCE=");
        }

        if (message.getBody().isPresent()) {
            System.out.println("Body: " + message.getBody().get());
        }
        if (message.getGroupContext().isPresent()) {
            System.out.println("Group info:");
            final SignalServiceGroupContext groupContext = message.getGroupContext().get();
            final GroupId groupId = GroupUtils.getGroupId(groupContext);
            if (groupContext.getGroupV1().isPresent()) {
                SignalServiceGroup groupInfo = groupContext.getGroupV1().get();
                System.out.println("  Id: " + groupId.toBase64());
                if (groupInfo.getType() == SignalServiceGroup.Type.UPDATE && groupInfo.getName().isPresent()) {
                    System.out.println("  Name: " + groupInfo.getName().get());
                } else {
                    GroupInfo group = m.getGroup(groupId);
                    if (group != null) {
                        System.out.println("  Name: " + group.getTitle());
                    } else {
                        System.out.println("  Name: <Unknown group>");
                    }
                }
                System.out.println("  Type: " + groupInfo.getType());
                if (groupInfo.getMembers().isPresent()) {
                    for (SignalServiceAddress member : groupInfo.getMembers().get()) {
                        System.out.println("  Member: " + member.getLegacyIdentifier());
                    }
                }
                if (groupInfo.getAvatar().isPresent()) {
                    System.out.println("  Avatar:");
                    printAttachment(groupInfo.getAvatar().get(), 2);
                }
            } else if (groupContext.getGroupV2().isPresent()) {
                final SignalServiceGroupV2 groupInfo = groupContext.getGroupV2().get();
                System.out.println("  Id: " + groupId.toBase64());
                GroupInfo group = m.getGroup(groupId);
                if (group != null) {
                    System.out.println("  Name: " + group.getTitle());
                } else {
                    System.out.println("  Name: <Unknown group>");
                }
                System.out.println("  Revision: " + groupInfo.getRevision());
                System.out.println("  Master key length: " + groupInfo.getMasterKey().serialize().length);
                System.out.println("  Has signed group change: " + groupInfo.hasSignedGroupChange());
            }
        }
        if (message.getGroupCallUpdate().isPresent()) {
            final SignalServiceDataMessage.GroupCallUpdate groupCallUpdate = message.getGroupCallUpdate().get();
            System.out.println("Group call update:");
            System.out.println(" - Era id: " + groupCallUpdate.getEraId());
        }
        if (message.getPreviews().isPresent()) {
            final List<SignalServiceDataMessage.Preview> previews = message.getPreviews().get();
            System.out.println("Previews:");
            for (SignalServiceDataMessage.Preview preview : previews) {
                System.out.println(" - Title: " + preview.getTitle());
                System.out.println(" - Url: " + preview.getUrl());
                if (preview.getImage().isPresent()) {
                    printAttachment(preview.getImage().get(), 1);
                }
            }
        }
        if (message.getSharedContacts().isPresent()) {
            final List<SharedContact> sharedContacts = message.getSharedContacts().get();
            System.out.println("Contacts:");
            for (SharedContact contact : sharedContacts) {
                System.out.println(" - Name:");
                SharedContact.Name name = contact.getName();
                if (name.getDisplay().isPresent() && !name.getDisplay().get().isBlank()) {
                    System.out.println("   - Display name: " + name.getDisplay().get());
                }
                if (name.getGiven().isPresent() && !name.getGiven().get().isBlank()) {
                    System.out.println("   - First name: " + name.getGiven().get());
                }
                if (name.getMiddle().isPresent() && !name.getMiddle().get().isBlank()) {
                    System.out.println("   - Middle name: " + name.getMiddle().get());
                }
                if (name.getFamily().isPresent() && !name.getFamily().get().isBlank()) {
                    System.out.println("   - Family name: " + name.getFamily().get());
                }
                if (name.getPrefix().isPresent() && !name.getPrefix().get().isBlank()) {
                    System.out.println("   - Prefix name: " + name.getPrefix().get());
                }
                if (name.getSuffix().isPresent() && !name.getSuffix().get().isBlank()) {
                    System.out.println("   - Suffix name: " + name.getSuffix().get());
                }

                if (contact.getAvatar().isPresent()) {
                    SharedContact.Avatar avatar = contact.getAvatar().get();
                    System.out.println(" - Avatar:");
                    printAttachment(avatar.getAttachment(), 3);
                    if (avatar.isProfile()) {
                        System.out.println("   - Is profile");
                    } else {
                        System.out.println("   - Is not a profile");
                    }
                }

                if (contact.getPhone().isPresent()) {
                    System.out.println(" - Phone details:");
                    for (SharedContact.Phone phone : contact.getPhone().get()) {
                        System.out.println("   - Phone:");
                        if (phone.getValue() != null) {
                            System.out.println("     - Number: " + phone.getValue());
                        }
                        if (phone.getType() != null) {
                            System.out.println("     - Type: " + phone.getType());
                        }
                        if (phone.getLabel().isPresent() && !phone.getLabel().get().isBlank()) {
                            System.out.println("     - Label: " + phone.getLabel().get());
                        }
                    }
                }

                if (contact.getEmail().isPresent()) {
                    System.out.println(" - Email details:");
                    for (SharedContact.Email email : contact.getEmail().get()) {
                        System.out.println("   - Email:");
                        if (email.getValue() != null) {
                            System.out.println("     - Value: " + email.getValue());
                        }
                        if (email.getType() != null) {
                            System.out.println("     - Type: " + email.getType());
                        }
                        if (email.getLabel().isPresent() && !email.getLabel().get().isBlank()) {
                            System.out.println("     - Label: " + email.getLabel().get());
                        }
                    }
                }

                if (contact.getAddress().isPresent()) {
                    System.out.println(" - Address details:");
                    for (SharedContact.PostalAddress address : contact.getAddress().get()) {
                        System.out.println("   - Address:");
                        if (address.getType() != null) {
                            System.out.println("     - Type: " + address.getType());
                        }
                        if (address.getLabel().isPresent() && !address.getLabel().get().isBlank()) {
                            System.out.println("     - Label: " + address.getLabel().get());
                        }
                        if (address.getStreet().isPresent() && !address.getStreet().get().isBlank()) {
                            System.out.println("     - Street: " + address.getStreet().get());
                        }
                        if (address.getPobox().isPresent() && !address.getPobox().get().isBlank()) {
                            System.out.println("     - Pobox: " + address.getPobox().get());
                        }
                        if (address.getNeighborhood().isPresent() && !address.getNeighborhood().get().isBlank()) {
                            System.out.println("     - Neighbourhood: " + address.getNeighborhood().get());
                        }
                        if (address.getCity().isPresent() && !address.getCity().get().isBlank()) {
                            System.out.println("     - City: " + address.getCity().get());
                        }
                        if (address.getRegion().isPresent() && !address.getRegion().get().isBlank()) {
                            System.out.println("     - Region: " + address.getRegion().get());
                        }
                        if (address.getPostcode().isPresent() && !address.getPostcode().get().isBlank()) {
                            System.out.println("     - Postcode: " + address.getPostcode().get());
                        }
                        if (address.getCountry().isPresent() && !address.getCountry().get().isBlank()) {
                            System.out.println("     - Country: " + address.getCountry().get());
                        }
                    }
                }

                System.out.println(" - Organisation: " + (
                        contact.getOrganization().isPresent() ? contact.getOrganization().get() : "-"
                ));
            }
        }
        if (message.getSticker().isPresent()) {
            final SignalServiceDataMessage.Sticker sticker = message.getSticker().get();
            System.out.println("Sticker:");
            System.out.println(" - Pack id: " + Base64.getEncoder().encodeToString(sticker.getPackId()));
            System.out.println(" - Pack key: " + Base64.getEncoder().encodeToString(sticker.getPackKey()));
            System.out.println(" - Sticker id: " + sticker.getStickerId());
            // TODO also download sticker image ??
        }
        if (message.isEndSession()) {
            System.out.println("Is end session");
        }
        if (message.isExpirationUpdate()) {
            System.out.println("Is Expiration update: " + message.isExpirationUpdate());
        }
        if (message.getExpiresInSeconds() > 0) {
            System.out.println("Expires in: " + message.getExpiresInSeconds() + " seconds");
        }
        if (message.getProfileKey().isPresent()) {
            System.out.println("Profile key update, key length:" + message.getProfileKey().get().length);
        }

        if (message.getReaction().isPresent()) {
            final SignalServiceDataMessage.Reaction reaction = message.getReaction().get();
            System.out.println("Reaction:");
            System.out.println(" - Emoji: " + reaction.getEmoji());
            System.out.println(" - Target author: " + m.resolveSignalServiceAddress(reaction.getTargetAuthor())
                    .getLegacyIdentifier());
            System.out.println(" - Target timestamp: " + reaction.getTargetSentTimestamp());
            System.out.println(" - Is remove: " + reaction.isRemove());
        }

        if (message.getQuote().isPresent()) {
            SignalServiceDataMessage.Quote quote = message.getQuote().get();
            System.out.println("Quote: (" + quote.getId() + ")");
            System.out.println(" Author: " + m.resolveSignalServiceAddress(quote.getAuthor()).getLegacyIdentifier());
            System.out.println(" Text: " + quote.getText());
            if (quote.getMentions() != null && quote.getMentions().size() > 0) {
                System.out.println(" Mentions: ");
                for (SignalServiceDataMessage.Mention mention : quote.getMentions()) {
                    printMention(mention, m, 1);
                }
            }
            if (quote.getAttachments().size() > 0) {
                System.out.println(" Attachments: ");
                for (SignalServiceDataMessage.Quote.QuotedAttachment attachment : quote.getAttachments()) {
                    System.out.println(" - Filename: " + attachment.getFileName());
                    System.out.println("   Type: " + attachment.getContentType());
                    System.out.println("   Thumbnail:");
                    if (attachment.getThumbnail() != null) {
                        printAttachment(attachment.getThumbnail(), 3);
                    }
                }
            }
        }

        if (message.getRemoteDelete().isPresent()) {
            final SignalServiceDataMessage.RemoteDelete remoteDelete = message.getRemoteDelete().get();
            System.out.println("Remote delete message: timestamp = " + remoteDelete.getTargetSentTimestamp());
        }
        if (message.getMentions().isPresent()) {
            System.out.println("Mentions: ");
            for (SignalServiceDataMessage.Mention mention : message.getMentions().get()) {
                printMention(mention, m, 0);
            }
        }

        if (message.getAttachments().isPresent()) {
            System.out.println("Attachments: ");
            for (SignalServiceAttachment attachment : message.getAttachments().get()) {
                printAttachment(attachment, 0);
            }
        }
    }

    /**
     * Prints the Signal mention information
     *
     * @param mention       is the Signal mention to print
     * @param m             is the Manager. used to resolve UUIDs into phone numbers if possible
     * @param leadingSpaces is the number of spaces you want the message to be indented by
     */
    private void printMention(SignalServiceDataMessage.Mention mention, Manager m, int leadingSpaces) {
        String spaces = " ".repeat(leadingSpaces);
        System.out.println(spaces + "- " + m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid(),
                null)).getLegacyIdentifier() + ": " + mention.getStart() + " (length: " + mention.getLength() + ")");
    }

    /**
     * Prints the Signal attachment information
     *
     * @param attachment    is the Signal attachment to print
     * @param leadingSpaces is the number of spaces you want the message to be indented by
     */
    private void printAttachment(SignalServiceAttachment attachment, int leadingSpaces) {
        String spaces = " ".repeat(leadingSpaces);
        System.out.println(spaces + "- " + attachment.getContentType() + " (" + (
                attachment.isPointer() ? "Pointer" : ""
        ) + (
                attachment.isStream() ? "Stream" : ""
        ) + ")");
        if (attachment.isPointer()) {
            final SignalServiceAttachmentPointer pointer = attachment.asPointer();
            System.out.println(spaces + "  Id: " + pointer.getRemoteId() + " Key length: " + pointer.getKey().length);
            System.out.println(spaces + "  Filename: " + (
                    pointer.getFileName().isPresent() ? pointer.getFileName().get() : "-"
            ));
            System.out.println(spaces + "  Size: " + (
                    pointer.getSize().isPresent() ? pointer.getSize().get() + " bytes" : "<unavailable>"
            ) + (
                    pointer.getPreview().isPresent() ? " (Preview is available: "
                            + pointer.getPreview().get().length
                            + " bytes)" : ""
            ));
            System.out.println(spaces + "  Voice note: " + (pointer.getVoiceNote() ? "yes" : "no"));
            System.out.println(spaces + "  Dimensions: " + pointer.getWidth() + "x" + pointer.getHeight());
            File file = m.getAttachmentFile(pointer.getRemoteId());
            if (file.exists()) {
                System.out.println(spaces + "  Stored plaintext in: " + file);
            }
        }
    }
}
