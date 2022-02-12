package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.actions.HandleAction;
import org.asamk.signal.manager.actions.RefreshPreKeysAction;
import org.asamk.signal.manager.actions.RenewSessionAction;
import org.asamk.signal.manager.actions.ResendMessageAction;
import org.asamk.signal.manager.actions.RetrieveProfileAction;
import org.asamk.signal.manager.actions.RetrieveStorageDataAction;
import org.asamk.signal.manager.actions.SendGroupInfoAction;
import org.asamk.signal.manager.actions.SendGroupInfoRequestAction;
import org.asamk.signal.manager.actions.SendReceiptAction;
import org.asamk.signal.manager.actions.SendRetryMessageRequestAction;
import org.asamk.signal.manager.actions.SendSyncBlockedListAction;
import org.asamk.signal.manager.actions.SendSyncConfigurationAction;
import org.asamk.signal.manager.actions.SendSyncContactsAction;
import org.asamk.signal.manager.actions.SendSyncGroupsAction;
import org.asamk.signal.manager.actions.SendSyncKeysAction;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.api.UntrustedIdentityException;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.jobs.RetrieveStickerPackJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class IncomingMessageHandler {

    private final static Logger logger = LoggerFactory.getLogger(IncomingMessageHandler.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    public IncomingMessageHandler(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public Pair<List<HandleAction>, Exception> handleRetryEnvelope(
            final SignalServiceEnvelope envelope,
            final boolean ignoreAttachments,
            final Manager.ReceiveMessageHandler handler
    ) {
        final List<HandleAction> actions = new ArrayList<>();
        if (envelope.isPreKeySignalMessage()) {
            actions.add(RefreshPreKeysAction.create());
        }

        SignalServiceContent content = null;
        if (!envelope.isReceipt()) {
            account.getIdentityKeyStore().setRetryingDecryption(true);
            try {
                content = dependencies.getCipher().decrypt(envelope);
            } catch (ProtocolUntrustedIdentityException e) {
                final var recipientId = account.getRecipientStore().resolveRecipient(e.getSender());
                final var exception = new UntrustedIdentityException(account.getRecipientStore()
                        .resolveRecipientAddress(recipientId), e.getSenderDevice());
                return new Pair<>(List.of(), exception);
            } catch (Exception e) {
                return new Pair<>(List.of(), e);
            } finally {
                account.getIdentityKeyStore().setRetryingDecryption(false);
            }
        }
        actions.addAll(checkAndHandleMessage(envelope, content, ignoreAttachments, handler, null));
        return new Pair<>(actions, null);
    }

    public Pair<List<HandleAction>, Exception> handleEnvelope(
            final SignalServiceEnvelope envelope,
            final boolean ignoreAttachments,
            final Manager.ReceiveMessageHandler handler
    ) {
        final var actions = new ArrayList<HandleAction>();
        if (envelope.hasSourceUuid()) {
            // Store uuid if we don't have it already
            // address/uuid in envelope is sent by server
            account.getRecipientStore().resolveRecipientTrusted(envelope.getSourceAddress());
        }
        SignalServiceContent content = null;
        Exception exception = null;
        if (!envelope.isReceipt()) {
            try {
                content = dependencies.getCipher().decrypt(envelope);
            } catch (ProtocolUntrustedIdentityException e) {
                final var recipientId = account.getRecipientStore().resolveRecipient(e.getSender());
                actions.add(new RetrieveProfileAction(recipientId));
                exception = new UntrustedIdentityException(account.getRecipientStore()
                        .resolveRecipientAddress(recipientId), e.getSenderDevice());
            } catch (ProtocolInvalidKeyIdException | ProtocolInvalidKeyException | ProtocolNoSessionException | ProtocolInvalidMessageException e) {
                logger.debug("Failed to decrypt incoming message", e);
                final var sender = account.getRecipientStore().resolveRecipient(e.getSender());
                if (context.getContactHelper().isContactBlocked(sender)) {
                    logger.debug("Received invalid message from blocked contact, ignoring.");
                } else {
                    final var senderProfile = context.getProfileHelper().getRecipientProfile(sender);
                    final var selfProfile = context.getProfileHelper()
                            .getRecipientProfile(account.getSelfRecipientId());
                    if ((!sender.equals(account.getSelfRecipientId()) || e.getSenderDevice() != account.getDeviceId())
                            && senderProfile != null
                            && senderProfile.getCapabilities().contains(Profile.Capability.senderKey)
                            && selfProfile != null
                            && selfProfile.getCapabilities().contains(Profile.Capability.senderKey)) {
                        logger.debug("Received invalid message, requesting message resend.");
                        actions.add(new SendRetryMessageRequestAction(sender, e, envelope));
                    } else {
                        logger.debug("Received invalid message, queuing renew session action.");
                        actions.add(new RenewSessionAction(sender));
                    }
                }
                exception = e;
            } catch (SelfSendException e) {
                logger.debug("Dropping unidentified message from self.");
                return new Pair<>(List.of(), null);
            } catch (Exception e) {
                logger.debug("Failed to handle incoming message", e);
                exception = e;
            }
        }

        actions.addAll(checkAndHandleMessage(envelope, content, ignoreAttachments, handler, exception));
        return new Pair<>(actions, exception);
    }

    private List<HandleAction> checkAndHandleMessage(
            final SignalServiceEnvelope envelope,
            final SignalServiceContent content,
            final boolean ignoreAttachments,
            final Manager.ReceiveMessageHandler handler,
            final Exception exception
    ) {
        if (!envelope.hasSourceUuid() && content != null) {
            // Store uuid if we don't have it already
            // address/uuid is validated by unidentified sender certificate
            account.getRecipientStore().resolveRecipientTrusted(content.getSender());
        }
        if (envelope.isReceipt()) {
            final var senderPair = getSender(envelope, content);
            final var sender = senderPair.first();
            final var senderDeviceId = senderPair.second();
            account.getMessageSendLogStore().deleteEntryForRecipient(envelope.getTimestamp(), sender, senderDeviceId);
        }

        if (isMessageBlocked(envelope, content)) {
            logger.info("Ignoring a message from blocked user/group: {}", envelope.getTimestamp());
            return List.of();
        } else if (isNotAllowedToSendToGroup(envelope, content)) {
            logger.info("Ignoring a group message from an unauthorized sender (no member or admin): {} {}",
                    (envelope.hasSourceUuid() ? envelope.getSourceAddress() : content.getSender()).getIdentifier(),
                    envelope.getTimestamp());
            return List.of();
        } else {
            List<HandleAction> actions;
            if (content != null) {
                actions = handleMessage(envelope, content, ignoreAttachments);
            } else {
                actions = List.of();
            }
            handler.handleMessage(MessageEnvelope.from(envelope,
                    content,
                    account.getRecipientStore(),
                    account.getRecipientStore()::resolveRecipientAddress,
                    context.getAttachmentHelper()::getAttachmentFile,
                    exception), exception);
            return actions;
        }
    }

    public List<HandleAction> handleMessage(
            SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        final var senderPair = getSender(envelope, content);
        final var sender = senderPair.first();
        final var senderDeviceId = senderPair.second();

        if (content.getReceiptMessage().isPresent()) {
            final var message = content.getReceiptMessage().get();
            if (message.isDeliveryReceipt()) {
                account.getMessageSendLogStore()
                        .deleteEntriesForRecipient(message.getTimestamps(), sender, senderDeviceId);
            }
        }

        if (content.getSenderKeyDistributionMessage().isPresent()) {
            final var message = content.getSenderKeyDistributionMessage().get();
            final var protocolAddress = new SignalProtocolAddress(context.getRecipientHelper()
                    .resolveSignalServiceAddress(sender)
                    .getIdentifier(), senderDeviceId);
            logger.debug("Received a sender key distribution message for distributionId {} from {}",
                    message.getDistributionId(),
                    protocolAddress);
            dependencies.getMessageSender().processSenderKeyDistributionMessage(protocolAddress, message);
        }

        if (content.getDecryptionErrorMessage().isPresent()) {
            var message = content.getDecryptionErrorMessage().get();
            logger.debug("Received a decryption error message from {}.{} (resend request for {})",
                    sender,
                    senderDeviceId,
                    message.getTimestamp());
            if (message.getDeviceId() == account.getDeviceId()) {
                handleDecryptionErrorMessage(actions, sender, senderDeviceId, message);
            } else {
                logger.debug("Request is for another one of our devices");
            }
        }

        if (content.getDataMessage().isPresent()) {
            var message = content.getDataMessage().get();

            if (content.isNeedsReceipt()) {
                actions.add(new SendReceiptAction(sender, message.getTimestamp()));
            }

            actions.addAll(handleSignalServiceDataMessage(message,
                    false,
                    sender,
                    account.getSelfRecipientId(),
                    ignoreAttachments));
        }

        if (content.getSyncMessage().isPresent()) {
            var syncMessage = content.getSyncMessage().get();
            actions.addAll(handleSyncMessage(syncMessage, sender, ignoreAttachments));
        }

        return actions;
    }

    private void handleDecryptionErrorMessage(
            final List<HandleAction> actions,
            final RecipientId sender,
            final int senderDeviceId,
            final DecryptionErrorMessage message
    ) {
        final var logEntries = account.getMessageSendLogStore()
                .findMessages(sender, senderDeviceId, message.getTimestamp(), !message.getRatchetKey().isPresent());

        for (final var logEntry : logEntries) {
            actions.add(new ResendMessageAction(sender, message.getTimestamp(), logEntry));
        }

        if (message.getRatchetKey().isPresent()) {
            if (account.getSessionStore().isCurrentRatchetKey(sender, senderDeviceId, message.getRatchetKey().get())) {
                if (logEntries.isEmpty()) {
                    logger.debug("Renewing the session with sender");
                    actions.add(new RenewSessionAction(sender));
                } else {
                    logger.trace("Archiving the session with sender, a resend message has already been queued");
                    context.getAccount().getSessionStore().archiveSessions(sender);
                }
            }
            return;
        }

        var found = false;
        for (final var logEntry : logEntries) {
            if (logEntry.groupId().isEmpty()) {
                continue;
            }
            final var group = account.getGroupStore().getGroup(logEntry.groupId().get());
            if (group == null) {
                continue;
            }
            found = true;
            logger.trace("Deleting shared sender key with {} ({}): {}",
                    sender,
                    senderDeviceId,
                    group.getDistributionId());
            account.getSenderKeyStore().deleteSharedWith(sender, senderDeviceId, group.getDistributionId());
        }
        if (!found) {
            logger.debug("Reset all shared sender keys with this recipient, no related message found in send log");
            account.getSenderKeyStore().deleteSharedWith(sender);
        }
    }

    private List<HandleAction> handleSyncMessage(
            final SignalServiceSyncMessage syncMessage, final RecipientId sender, final boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        account.setMultiDevice(true);
        if (syncMessage.getSent().isPresent()) {
            var message = syncMessage.getSent().get();
            final var destination = message.getDestination().orNull();
            actions.addAll(handleSignalServiceDataMessage(message.getMessage(),
                    true,
                    sender,
                    destination == null ? null : context.getRecipientHelper().resolveRecipient(destination),
                    ignoreAttachments));
        }
        if (syncMessage.getRequest().isPresent() && account.isMasterDevice()) {
            var rm = syncMessage.getRequest().get();
            if (rm.isContactsRequest()) {
                actions.add(SendSyncContactsAction.create());
            }
            if (rm.isGroupsRequest()) {
                actions.add(SendSyncGroupsAction.create());
            }
            if (rm.isBlockedListRequest()) {
                actions.add(SendSyncBlockedListAction.create());
            }
            if (rm.isKeysRequest()) {
                actions.add(SendSyncKeysAction.create());
            }
            if (rm.isConfigurationRequest()) {
                actions.add(SendSyncConfigurationAction.create());
            }
        }
        if (syncMessage.getGroups().isPresent()) {
            logger.warn("Received a group v1 sync message, that can't be handled anymore, ignoring.");
        }
        if (syncMessage.getBlockedList().isPresent()) {
            final var blockedListMessage = syncMessage.getBlockedList().get();
            for (var address : blockedListMessage.getAddresses()) {
                context.getContactHelper()
                        .setContactBlocked(context.getRecipientHelper().resolveRecipient(address), true);
            }
            for (var groupId : blockedListMessage.getGroupIds()
                    .stream()
                    .map(GroupId::unknownVersion)
                    .collect(Collectors.toSet())) {
                try {
                    context.getGroupHelper().setGroupBlocked(groupId, true);
                } catch (GroupNotFoundException e) {
                    logger.warn("BlockedListMessage contained groupID that was not found in GroupStore: {}",
                            groupId.toBase64());
                }
            }
        }
        if (syncMessage.getContacts().isPresent()) {
            try {
                final var contactsMessage = syncMessage.getContacts().get();
                context.getAttachmentHelper()
                        .retrieveAttachment(contactsMessage.getContactsStream(),
                                context.getSyncHelper()::handleSyncDeviceContacts);
            } catch (Exception e) {
                logger.warn("Failed to handle received sync contacts, ignoring: {}", e.getMessage());
            }
        }
        if (syncMessage.getVerified().isPresent()) {
            final var verifiedMessage = syncMessage.getVerified().get();
            account.getIdentityKeyStore()
                    .setIdentityTrustLevel(account.getRecipientStore()
                                    .resolveRecipientTrusted(verifiedMessage.getDestination()),
                            verifiedMessage.getIdentityKey(),
                            TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
        }
        if (syncMessage.getStickerPackOperations().isPresent()) {
            final var stickerPackOperationMessages = syncMessage.getStickerPackOperations().get();
            for (var m : stickerPackOperationMessages) {
                if (!m.getPackId().isPresent()) {
                    continue;
                }
                final var stickerPackId = StickerPackId.deserialize(m.getPackId().get());
                final var installed = !m.getType().isPresent()
                        || m.getType().get() == StickerPackOperationMessage.Type.INSTALL;

                var sticker = account.getStickerStore().getStickerPack(stickerPackId);
                if (m.getPackKey().isPresent()) {
                    if (sticker == null) {
                        sticker = new Sticker(stickerPackId, m.getPackKey().get());
                    }
                    if (installed) {
                        context.getJobExecutor()
                                .enqueueJob(new RetrieveStickerPackJob(stickerPackId, m.getPackKey().get()));
                    }
                }

                if (sticker != null) {
                    sticker.setInstalled(installed);
                    account.getStickerStore().updateSticker(sticker);
                }
            }
        }
        if (syncMessage.getFetchType().isPresent()) {
            switch (syncMessage.getFetchType().get()) {
                case LOCAL_PROFILE:
                    actions.add(new RetrieveProfileAction(account.getSelfRecipientId()));
                case STORAGE_MANIFEST:
                    actions.add(RetrieveStorageDataAction.create());
            }
        }
        if (syncMessage.getKeys().isPresent()) {
            final var keysMessage = syncMessage.getKeys().get();
            if (keysMessage.getStorageService().isPresent()) {
                final var storageKey = keysMessage.getStorageService().get();
                account.setStorageKey(storageKey);
                actions.add(RetrieveStorageDataAction.create());
            }
        }
        if (syncMessage.getConfiguration().isPresent()) {
            final var configurationMessage = syncMessage.getConfiguration().get();
            final var configurationStore = account.getConfigurationStore();
            if (configurationMessage.getReadReceipts().isPresent()) {
                configurationStore.setReadReceipts(configurationMessage.getReadReceipts().get());
            }
            if (configurationMessage.getLinkPreviews().isPresent()) {
                configurationStore.setLinkPreviews(configurationMessage.getLinkPreviews().get());
            }
            if (configurationMessage.getTypingIndicators().isPresent()) {
                configurationStore.setTypingIndicators(configurationMessage.getTypingIndicators().get());
            }
            if (configurationMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
                configurationStore.setUnidentifiedDeliveryIndicators(configurationMessage.getUnidentifiedDeliveryIndicators()
                        .get());
            }
        }
        return actions;
    }

    private boolean isMessageBlocked(SignalServiceEnvelope envelope, SignalServiceContent content) {
        SignalServiceAddress source;
        if (!envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) {
            source = envelope.getSourceAddress();
        } else if (content != null) {
            source = content.getSender();
        } else {
            return false;
        }
        final var recipientId = context.getRecipientHelper().resolveRecipient(source);
        if (context.getContactHelper().isContactBlocked(recipientId)) {
            return true;
        }

        if (content != null && content.getDataMessage().isPresent()) {
            var message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent()) {
                var groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                return context.getGroupHelper().isGroupBlocked(groupId);
            }
        }

        return false;
    }

    private boolean isNotAllowedToSendToGroup(SignalServiceEnvelope envelope, SignalServiceContent content) {
        SignalServiceAddress source;
        if (!envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) {
            source = envelope.getSourceAddress();
        } else if (content != null) {
            source = content.getSender();
        } else {
            return false;
        }

        if (content == null || !content.getDataMessage().isPresent()) {
            return false;
        }

        var message = content.getDataMessage().get();
        if (!message.getGroupContext().isPresent()) {
            return false;
        }

        if (message.getGroupContext().get().getGroupV1().isPresent()) {
            var groupInfo = message.getGroupContext().get().getGroupV1().get();
            if (groupInfo.getType() == SignalServiceGroup.Type.QUIT) {
                return false;
            }
        }

        var groupId = GroupUtils.getGroupId(message.getGroupContext().get());
        var group = context.getGroupHelper().getGroup(groupId);
        if (group == null) {
            return false;
        }

        final var recipientId = context.getRecipientHelper().resolveRecipient(source);
        if (!group.isMember(recipientId) && !(group.isPendingMember(recipientId) && message.isGroupV2Update())) {
            return true;
        }

        if (group.isAnnouncementGroup() && !group.isAdmin(recipientId)) {
            return message.getBody().isPresent()
                    || message.getAttachments().isPresent()
                    || message.getQuote()
                    .isPresent()
                    || message.getPreviews().isPresent()
                    || message.getMentions().isPresent()
                    || message.getSticker().isPresent();
        }
        return false;
    }

    private List<HandleAction> handleSignalServiceDataMessage(
            SignalServiceDataMessage message,
            boolean isSync,
            RecipientId source,
            RecipientId destination,
            boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        if (message.getGroupContext().isPresent()) {
            if (message.getGroupContext().get().getGroupV1().isPresent()) {
                var groupInfo = message.getGroupContext().get().getGroupV1().get();
                var groupId = GroupId.v1(groupInfo.getGroupId());
                var group = context.getGroupHelper().getGroup(groupId);
                if (group == null || group instanceof GroupInfoV1) {
                    var groupV1 = (GroupInfoV1) group;
                    switch (groupInfo.getType()) {
                        case UPDATE: {
                            if (groupV1 == null) {
                                groupV1 = new GroupInfoV1(groupId);
                            }

                            if (groupInfo.getAvatar().isPresent()) {
                                var avatar = groupInfo.getAvatar().get();
                                context.getGroupHelper().downloadGroupAvatar(groupV1.getGroupId(), avatar);
                            }

                            if (groupInfo.getName().isPresent()) {
                                groupV1.name = groupInfo.getName().get();
                            }

                            if (groupInfo.getMembers().isPresent()) {
                                groupV1.addMembers(groupInfo.getMembers()
                                        .get()
                                        .stream()
                                        .map(context.getRecipientHelper()::resolveRecipient)
                                        .collect(Collectors.toSet()));
                            }

                            account.getGroupStore().updateGroup(groupV1);
                            break;
                        }
                        case DELIVER:
                            if (groupV1 == null && !isSync) {
                                actions.add(new SendGroupInfoRequestAction(source, groupId));
                            }
                            break;
                        case QUIT: {
                            if (groupV1 != null) {
                                groupV1.removeMember(source);
                                account.getGroupStore().updateGroup(groupV1);
                            }
                            break;
                        }
                        case REQUEST_INFO:
                            if (groupV1 != null && !isSync) {
                                actions.add(new SendGroupInfoAction(source, groupV1.getGroupId()));
                            }
                            break;
                    }
                } else {
                    // Received a group v1 message for a v2 group
                }
            }
            if (message.getGroupContext().get().getGroupV2().isPresent()) {
                final var groupContext = message.getGroupContext().get().getGroupV2().get();
                final var groupMasterKey = groupContext.getMasterKey();

                context.getGroupHelper()
                        .getOrMigrateGroup(groupMasterKey,
                                groupContext.getRevision(),
                                groupContext.hasSignedGroupChange() ? groupContext.getSignedGroupChange() : null);
            }
        }

        final var conversationPartnerAddress = isSync ? destination : source;
        if (conversationPartnerAddress != null && message.isEndSession()) {
            account.getSessionStore().deleteAllSessions(conversationPartnerAddress);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            if (message.getGroupContext().isPresent()) {
                if (message.getGroupContext().get().getGroupV1().isPresent()) {
                    var groupInfo = message.getGroupContext().get().getGroupV1().get();
                    var group = account.getGroupStore().getOrCreateGroupV1(GroupId.v1(groupInfo.getGroupId()));
                    if (group != null) {
                        if (group.messageExpirationTime != message.getExpiresInSeconds()) {
                            group.messageExpirationTime = message.getExpiresInSeconds();
                            account.getGroupStore().updateGroup(group);
                        }
                    }
                } else if (message.getGroupContext().get().getGroupV2().isPresent()) {
                    // disappearing message timer already stored in the DecryptedGroup
                }
            } else if (conversationPartnerAddress != null) {
                context.getContactHelper()
                        .setExpirationTimer(conversationPartnerAddress, message.getExpiresInSeconds());
            }
        }
        if (!ignoreAttachments) {
            if (message.getAttachments().isPresent()) {
                for (var attachment : message.getAttachments().get()) {
                    context.getAttachmentHelper().downloadAttachment(attachment);
                }
            }
            if (message.getSharedContacts().isPresent()) {
                for (var contact : message.getSharedContacts().get()) {
                    if (contact.getAvatar().isPresent()) {
                        context.getAttachmentHelper().downloadAttachment(contact.getAvatar().get().getAttachment());
                    }
                }
            }
            if (message.getPreviews().isPresent()) {
                final var previews = message.getPreviews().get();
                for (var preview : previews) {
                    if (preview.getImage().isPresent()) {
                        context.getAttachmentHelper().downloadAttachment(preview.getImage().get());
                    }
                }
            }
            if (message.getQuote().isPresent()) {
                final var quote = message.getQuote().get();

                for (var quotedAttachment : quote.getAttachments()) {
                    final var thumbnail = quotedAttachment.getThumbnail();
                    if (thumbnail != null) {
                        context.getAttachmentHelper().downloadAttachment(thumbnail);
                    }
                }
            }
        }
        if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            final ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(message.getProfileKey().get());
            } catch (InvalidInputException e) {
                throw new AssertionError(e);
            }
            if (account.getSelfRecipientId().equals(source)) {
                this.account.setProfileKey(profileKey);
            }
            this.account.getProfileStore().storeProfileKey(source, profileKey);
        }
        if (message.getSticker().isPresent()) {
            final var messageSticker = message.getSticker().get();
            final var stickerPackId = StickerPackId.deserialize(messageSticker.getPackId());
            var sticker = account.getStickerStore().getStickerPack(stickerPackId);
            if (sticker == null) {
                sticker = new Sticker(stickerPackId, messageSticker.getPackKey());
                account.getStickerStore().updateSticker(sticker);
            }
            context.getJobExecutor().enqueueJob(new RetrieveStickerPackJob(stickerPackId, messageSticker.getPackKey()));
        }
        return actions;
    }

    private Pair<RecipientId, Integer> getSender(SignalServiceEnvelope envelope, SignalServiceContent content) {
        if (!envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) {
            return new Pair<>(context.getRecipientHelper().resolveRecipient(envelope.getSourceAddress()),
                    envelope.getSourceDevice());
        } else {
            return new Pair<>(context.getRecipientHelper().resolveRecipient(content.getSender()),
                    content.getSenderDevice());
        }
    }
}
