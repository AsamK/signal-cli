package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.JobExecutor;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.actions.HandleAction;
import org.asamk.signal.manager.actions.RefreshPreKeysAction;
import org.asamk.signal.manager.actions.RenewSessionAction;
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
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.jobs.RetrieveStickerPackJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.storage.stickers.Sticker;
import org.asamk.signal.manager.storage.stickers.StickerPackId;
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
import org.whispersystems.libsignal.util.Pair;
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
    private final RecipientResolver recipientResolver;
    private final SignalServiceAddressResolver addressResolver;
    private final GroupHelper groupHelper;
    private final ContactHelper contactHelper;
    private final AttachmentHelper attachmentHelper;
    private final SyncHelper syncHelper;
    private final ProfileProvider profileProvider;
    private final JobExecutor jobExecutor;

    public IncomingMessageHandler(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final RecipientResolver recipientResolver,
            final SignalServiceAddressResolver addressResolver,
            final GroupHelper groupHelper,
            final ContactHelper contactHelper,
            final AttachmentHelper attachmentHelper,
            final SyncHelper syncHelper,
            final ProfileProvider profileProvider,
            final JobExecutor jobExecutor
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.recipientResolver = recipientResolver;
        this.addressResolver = addressResolver;
        this.groupHelper = groupHelper;
        this.contactHelper = contactHelper;
        this.attachmentHelper = attachmentHelper;
        this.syncHelper = syncHelper;
        this.profileProvider = profileProvider;
        this.jobExecutor = jobExecutor;
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
            try {
                content = dependencies.getCipher().decrypt(envelope);
            } catch (ProtocolUntrustedIdentityException e) {
                final var recipientId = account.getRecipientStore().resolveRecipient(e.getSender());
                final var exception = new UntrustedIdentityException(addressResolver.resolveSignalServiceAddress(
                        recipientId), e.getSenderDevice());
                return new Pair<>(List.of(), exception);
            } catch (Exception e) {
                return new Pair<>(List.of(), e);
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
                exception = new UntrustedIdentityException(addressResolver.resolveSignalServiceAddress(recipientId),
                        e.getSenderDevice());
            } catch (ProtocolInvalidKeyIdException | ProtocolInvalidKeyException | ProtocolNoSessionException | ProtocolInvalidMessageException e) {
                final var sender = account.getRecipientStore().resolveRecipient(e.getSender());
                final var senderProfile = profileProvider.getProfile(sender);
                final var selfProfile = profileProvider.getProfile(account.getSelfRecipientId());
                if (e.getSenderDevice() != account.getDeviceId()
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
                exception = e;
            } catch (SelfSendException e) {
                logger.debug("Dropping unidentified message from self.");
                return new Pair<>(List.of(), null);
            } catch (Exception e) {
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
            handler.handleMessage(envelope, content, exception);
            return actions;
        }
    }

    public List<HandleAction> handleMessage(
            SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments
    ) {
        var actions = new ArrayList<HandleAction>();
        final RecipientId sender;
        final int senderDeviceId;
        if (!envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) {
            sender = recipientResolver.resolveRecipient(envelope.getSourceAddress());
            senderDeviceId = envelope.getSourceDevice();
        } else {
            sender = recipientResolver.resolveRecipient(content.getSender());
            senderDeviceId = content.getSenderDevice();
        }

        if (content.getSenderKeyDistributionMessage().isPresent()) {
            final var message = content.getSenderKeyDistributionMessage().get();
            final var protocolAddress = new SignalProtocolAddress(addressResolver.resolveSignalServiceAddress(sender)
                    .getIdentifier(), senderDeviceId);
            dependencies.getMessageSender().processSenderKeyDistributionMessage(protocolAddress, message);
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
                    destination == null ? null : recipientResolver.resolveRecipient(destination),
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
                contactHelper.setContactBlocked(recipientResolver.resolveRecipient(address), true);
            }
            for (var groupId : blockedListMessage.getGroupIds()
                    .stream()
                    .map(GroupId::unknownVersion)
                    .collect(Collectors.toSet())) {
                try {
                    groupHelper.setGroupBlocked(groupId, true);
                } catch (GroupNotFoundException e) {
                    logger.warn("BlockedListMessage contained groupID that was not found in GroupStore: {}",
                            groupId.toBase64());
                }
            }
        }
        if (syncMessage.getContacts().isPresent()) {
            try {
                final var contactsMessage = syncMessage.getContacts().get();
                attachmentHelper.retrieveAttachment(contactsMessage.getContactsStream(),
                        syncHelper::handleSyncDeviceContacts);
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

                var sticker = account.getStickerStore().getSticker(stickerPackId);
                if (m.getPackKey().isPresent()) {
                    if (sticker == null) {
                        sticker = new Sticker(stickerPackId, m.getPackKey().get());
                    }
                    if (installed) {
                        jobExecutor.enqueueJob(new RetrieveStickerPackJob(stickerPackId, m.getPackKey().get()));
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
        final var recipientId = recipientResolver.resolveRecipient(source);
        if (contactHelper.isContactBlocked(recipientId)) {
            return true;
        }

        if (content != null && content.getDataMessage().isPresent()) {
            var message = content.getDataMessage().get();
            if (message.getGroupContext().isPresent()) {
                var groupId = GroupUtils.getGroupId(message.getGroupContext().get());
                return groupHelper.isGroupBlocked(groupId);
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
        var group = groupHelper.getGroup(groupId);
        if (group == null) {
            return false;
        }

        final var recipientId = recipientResolver.resolveRecipient(source);
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
                var group = groupHelper.getGroup(groupId);
                if (group == null || group instanceof GroupInfoV1) {
                    var groupV1 = (GroupInfoV1) group;
                    switch (groupInfo.getType()) {
                        case UPDATE: {
                            if (groupV1 == null) {
                                groupV1 = new GroupInfoV1(groupId);
                            }

                            if (groupInfo.getAvatar().isPresent()) {
                                var avatar = groupInfo.getAvatar().get();
                                groupHelper.downloadGroupAvatar(groupV1.getGroupId(), avatar);
                            }

                            if (groupInfo.getName().isPresent()) {
                                groupV1.name = groupInfo.getName().get();
                            }

                            if (groupInfo.getMembers().isPresent()) {
                                groupV1.addMembers(groupInfo.getMembers()
                                        .get()
                                        .stream()
                                        .map(recipientResolver::resolveRecipient)
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

                groupHelper.getOrMigrateGroup(groupMasterKey,
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
                contactHelper.setExpirationTimer(conversationPartnerAddress, message.getExpiresInSeconds());
            }
        }
        if (!ignoreAttachments) {
            if (message.getAttachments().isPresent()) {
                for (var attachment : message.getAttachments().get()) {
                    attachmentHelper.downloadAttachment(attachment);
                }
            }
            if (message.getSharedContacts().isPresent()) {
                for (var contact : message.getSharedContacts().get()) {
                    if (contact.getAvatar().isPresent()) {
                        attachmentHelper.downloadAttachment(contact.getAvatar().get().getAttachment());
                    }
                }
            }
            if (message.getPreviews().isPresent()) {
                final var previews = message.getPreviews().get();
                for (var preview : previews) {
                    if (preview.getImage().isPresent()) {
                        attachmentHelper.downloadAttachment(preview.getImage().get());
                    }
                }
            }
            if (message.getQuote().isPresent()) {
                final var quote = message.getQuote().get();

                for (var quotedAttachment : quote.getAttachments()) {
                    final var thumbnail = quotedAttachment.getThumbnail();
                    if (thumbnail != null) {
                        attachmentHelper.downloadAttachment(thumbnail);
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
            var sticker = account.getStickerStore().getSticker(stickerPackId);
            if (sticker == null) {
                sticker = new Sticker(stickerPackId, messageSticker.getPackKey());
                account.getStickerStore().updateSticker(sticker);
            }
            jobExecutor.enqueueJob(new RetrieveStickerPackJob(stickerPackId, messageSticker.getPackKey()));
        }
        return actions;
    }
}
