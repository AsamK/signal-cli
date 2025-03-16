package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogEntry;
import org.jetbrains.annotations.Nullable;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupSendEndorsements;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEditMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.signalservice.internal.push.http.PartialSendCompleteListener;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import okio.ByteString;

public class SendHelper {

    private static final Logger logger = LoggerFactory.getLogger(SendHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    public SendHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    /**
     * Send a single message to one recipient.
     * The message is extended with the current expiration timer.
     */
    public SendMessageResult sendMessage(
            final SignalServiceDataMessage.Builder messageBuilder,
            final RecipientId recipientId,
            Optional<Long> editTargetTimestamp
    ) {
        var contact = account.getContactStore().getContact(recipientId);
        if (contact == null || !contact.isProfileSharingEnabled() || contact.isHidden()) {
            final var contactBuilder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            contact = contactBuilder.withIsProfileSharingEnabled(true).withIsHidden(false).build();
            account.getContactStore().storeContact(recipientId, contact);
        }

        messageBuilder.withExpiration(contact.messageExpirationTime());
        messageBuilder.withExpireTimerVersion(contact.messageExpirationTimeVersion());

        if (!contact.isBlocked()) {
            final var profileKey = account.getProfileKey().serialize();
            messageBuilder.withProfileKey(profileKey);
        }

        final var message = messageBuilder.build();
        return sendMessage(message, recipientId, editTargetTimestamp);
    }

    /**
     * Send a group message to the given group
     * The message is extended with the current expiration timer for the group and the group context.
     */
    public List<SendMessageResult> sendAsGroupMessage(
            final SignalServiceDataMessage.Builder messageBuilder,
            final GroupId groupId,
            final boolean includeSelf,
            final Optional<Long> editTargetTimestamp
    ) throws IOException, GroupNotFoundException, NotAGroupMemberException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        return sendAsGroupMessage(messageBuilder, g, includeSelf, editTargetTimestamp);
    }

    /**
     * Send a complete group message to the given recipients (should be current/old/new members)
     * This method should only be used for create/update/quit group messages.
     */
    public List<SendMessageResult> sendGroupMessage(
            final SignalServiceDataMessage message,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId
    ) throws IOException {
        return sendGroupMessage(message, recipientIds, distributionId, ContentHint.IMPLICIT, Optional.empty());
    }

    public SendMessageResult sendReceiptMessage(
            final SignalServiceReceiptMessage receiptMessage,
            final RecipientId recipientId
    ) {
        final var messageSendLogStore = account.getMessageSendLogStore();
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendReceipt(address,
                        unidentifiedAccess,
                        receiptMessage,
                        includePniSignature));
        messageSendLogStore.insertIfPossible(receiptMessage.getWhen(), result, ContentHint.IMPLICIT, false);
        handleSendMessageResult(result);
        return result;
    }

    public SendMessageResult sendProfileKey(RecipientId recipientId) {
        logger.debug("Sending updated profile key to recipient: {}", recipientId);
        final var profileKey = account.getProfileKey().serialize();
        final var message = SignalServiceDataMessage.newBuilder()
                .asProfileKeyUpdate(true)
                .withProfileKey(profileKey)
                .build();
        return handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendDataMessage(
                        address,
                        unidentifiedAccess,
                        ContentHint.IMPLICIT,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY,
                        false,
                        includePniSignature));
    }

    public SendMessageResult sendRetryReceipt(
            DecryptionErrorMessage errorMessage,
            RecipientId recipientId,
            Optional<GroupId> groupId
    ) {
        logger.debug("Sending retry receipt for {} to {}, device: {}",
                errorMessage.getTimestamp(),
                recipientId,
                errorMessage.getDeviceId());
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendRetryReceipt(
                        address,
                        unidentifiedAccess,
                        groupId.map(GroupId::serialize),
                        errorMessage));
        handleSendMessageResult(result);
        return result;
    }

    public SendMessageResult sendNullMessage(RecipientId recipientId) {
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendNullMessage(
                        address,
                        unidentifiedAccess));
        handleSendMessageResult(result);
        return result;
    }

    public SendMessageResult sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder,
            Optional<Long> editTargetTimestamp
    ) {
        final var recipientId = account.getSelfRecipientId();
        final var contact = account.getContactStore().getContact(recipientId);
        messageBuilder.withExpiration(contact != null ? contact.messageExpirationTime() : 0);
        messageBuilder.withExpireTimerVersion(contact != null ? contact.messageExpirationTimeVersion() : 1);

        var message = messageBuilder.build();
        return sendSelfMessage(message, editTargetTimestamp);
    }

    public SendMessageResult sendSyncMessage(SignalServiceSyncMessage message) {
        var messageSender = dependencies.getMessageSender();
        if (!account.isMultiDevice()) {
            logger.trace("Not sending sync message because there are no linked devices.");
            return SendMessageResult.success(account.getSelfAddress(), List.of(), false, false, 0, Optional.empty());
        }
        try {
            return messageSender.sendSyncMessage(message);
        } catch (Throwable e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            try {
                return SignalServiceMessageSender.mapSendErrorToSendResult(e, System.currentTimeMillis(), address);
            } catch (IOException ex) {
                logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
                logger.debug("Exception", e);
                return SendMessageResult.networkFailure(address);
            }
        }
    }

    public SendMessageResult sendTypingMessage(SignalServiceTypingMessage message, RecipientId recipientId) {
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendTyping(List.of(
                        address), List.of(unidentifiedAccess), message, null).getFirst());
        handleSendMessageResult(result);
        return result;
    }

    public List<SendMessageResult> sendGroupTypingMessage(
            SignalServiceTypingMessage message,
            GroupId groupId
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        if (g.isAnnouncementGroup() && !g.isAdmin(account.getSelfRecipientId())) {
            throw new GroupSendingNotAllowedException(groupId, g.getTitle());
        }
        final var distributionId = g.getDistributionId();
        final var recipientIds = g.getMembersWithout(account.getSelfRecipientId());

        return sendGroupTypingMessage(message, recipientIds, distributionId);
    }

    public SendMessageResult resendMessage(
            final RecipientId recipientId,
            final long timestamp,
            final MessageSendLogEntry messageSendLogEntry
    ) {
        logger.trace("Resending message {} to {}", timestamp, recipientId);
        if (messageSendLogEntry.groupId().isEmpty()) {
            return handleSendMessage(recipientId,
                    (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.resendContent(
                            address,
                            unidentifiedAccess,
                            timestamp,
                            messageSendLogEntry.content(),
                            messageSendLogEntry.contentHint(),
                            Optional.empty(),
                            messageSendLogEntry.urgent()));
        }

        final var groupId = messageSendLogEntry.groupId().get();
        final var group = context.getGroupHelper().getGroup(groupId);

        if (group == null) {
            logger.debug("Could not find a matching group for the groupId {}! Skipping message send.",
                    groupId.toBase64());
            return null;
        } else if (!group.getMembers().contains(recipientId)) {
            logger.warn("The target user is no longer in the group {}! Skipping message send.", groupId.toBase64());
            return null;
        }

        final var senderKeyDistributionMessage = dependencies.getMessageSender()
                .getOrCreateNewGroupSession(group.getDistributionId());
        final var distributionBytes = ByteString.of(senderKeyDistributionMessage.serialize());
        final var contentToSend = messageSendLogEntry.content()
                .newBuilder()
                .senderKeyDistributionMessage(distributionBytes)
                .build();

        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.resendContent(address,
                        unidentifiedAccess,
                        timestamp,
                        contentToSend,
                        messageSendLogEntry.contentHint(),
                        Optional.of(group.getGroupId().serialize()),
                        messageSendLogEntry.urgent()));

        if (result.isSuccess()) {
            final var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
            final var addresses = result.getSuccess()
                    .getDevices()
                    .stream()
                    .map(device -> new SignalProtocolAddress(address.getIdentifier(), device))
                    .toList();

            account.getSenderKeyStore().markSenderKeySharedWith(group.getDistributionId(), addresses);
        }

        return result;
    }

    private List<SendMessageResult> sendAsGroupMessage(
            final SignalServiceDataMessage.Builder messageBuilder,
            final GroupInfo g,
            final boolean includeSelf,
            final Optional<Long> editTargetTimestamp
    ) throws IOException, GroupSendingNotAllowedException {
        GroupUtils.setGroupContext(messageBuilder, g);
        messageBuilder.withExpiration(g.getMessageExpirationTimer());

        final var message = messageBuilder.build();
        final var recipients = includeSelf ? g.getMembers() : g.getMembersWithout(account.getSelfRecipientId());

        if (g.isAnnouncementGroup() && !g.isAdmin(account.getSelfRecipientId())) {
            if (message.getBody().isPresent()
                    || message.getAttachments().isPresent()
                    || message.getQuote().isPresent()
                    || message.getPreviews().isPresent()
                    || message.getMentions().isPresent()
                    || message.getSticker().isPresent()) {
                throw new GroupSendingNotAllowedException(g.getGroupId(), g.getTitle());
            }
        }

        return sendGroupMessage(message,
                recipients,
                g.getDistributionId(),
                ContentHint.RESENDABLE,
                editTargetTimestamp);
    }

    public List<SendMessageResult> sendGroupStoryMessage(
            final SignalServiceStoryMessage message,
            final GroupInfo g
    ) throws IOException {
        final var messageSender = dependencies.getMessageSender();
        final var messageSendLogStore = account.getMessageSendLogStore();
        final AtomicLong entryId = new AtomicLong(-1);
        final boolean urgent = true;
        final long timestamp = System.currentTimeMillis();
        // remove sender/self
        final Set<RecipientId> recipientIds = g.getMembersWithout(account.getSelfRecipientId());
        final List<String> distributionListIds = List.of(g.getGroupId().toBase64());
        final Set<SignalServiceStoryMessageRecipient> messageRecipients = recipientIds.stream().map(i -> {
        	SignalServiceAddress ssa = context.getRecipientHelper().resolveSignalServiceAddress(i);
        	return new SignalServiceStoryMessageRecipient(ssa, distributionListIds, true);
        }).collect(Collectors.toSet());
        final SenderKeySenderHandler senderKeySender = (distId, recipients, unidentifiedAccess, groupSendEndorsements, isRecipientUpdate) -> messageSender.sendGroupStory(
                g.getDistributionId(),
                Optional.of(g.getGroupId().serialize()),
                recipients,
                unidentifiedAccess,
                groupSendEndorsements,
                true,
                message,
                timestamp,
                messageRecipients,
                sendResult -> {
                    logger.trace("Partial message send results: {}", sendResult.size());
                    synchronized (entryId) {
                        if (entryId.get() == -1) {
                            final var newId = messageSendLogStore.insertIfPossible(timestamp,
                                    sendResult,
                                    ContentHint.RESENDABLE,
                                    urgent);
                            entryId.set(newId);
                        } else {
                            messageSendLogStore.addRecipientToExistingEntryIfPossible(entryId.get(), sendResult);
                        }
                    }
                    synchronized (entryId) {
                        if (entryId.get() == -1) {
                            final var newId = messageSendLogStore.insertIfPossible(timestamp,
                                    sendResult,
                                    ContentHint.RESENDABLE,
                                    urgent);
                            entryId.set(newId);
                        } else {
                            messageSendLogStore.addRecipientToExistingEntryIfPossible(entryId.get(), sendResult);
                        }
                    }
                });
        final var results = sendStoryMessageInternal(senderKeySender, recipientIds, g.getDistributionId());

        for (var r : results) {
            handleSendMessageResult(r);
        }

        return results;
    }

    private List<SendMessageResult> sendGroupMessage(
            final SignalServiceDataMessage message,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId,
            final ContentHint contentHint,
            final Optional<Long> editTargetTimestamp
    ) throws IOException {
        final var messageSender = dependencies.getMessageSender();
        final var messageSendLogStore = account.getMessageSendLogStore();
        final AtomicLong entryId = new AtomicLong(-1);

        final var urgent = true;
        final PartialSendCompleteListener partialSendCompleteListener = sendResult -> {
            logger.trace("Partial message send result: {}", sendResult.isSuccess());
            synchronized (entryId) {
                if (entryId.get() == -1) {
                    final var newId = messageSendLogStore.insertIfPossible(message.getTimestamp(),
                            sendResult,
                            contentHint,
                            urgent);
                    entryId.set(newId);
                } else {
                    messageSendLogStore.addRecipientToExistingEntryIfPossible(entryId.get(), sendResult);
                }
            }
        };
        final LegacySenderHandler legacySender = (recipients, unidentifiedAccess, isRecipientUpdate) ->
                editTargetTimestamp.isEmpty()
                        ? messageSender.sendDataMessage(recipients,
                        unidentifiedAccess,
                        isRecipientUpdate,
                        contentHint,
                        message,
                        SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                        partialSendCompleteListener,
                        () -> false,
                        urgent)
                        : messageSender.sendEditMessage(recipients,
                                unidentifiedAccess,
                                isRecipientUpdate,
                                contentHint,
                                message,
                                SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                                partialSendCompleteListener,
                                () -> false,
                                urgent,
                                editTargetTimestamp.get());
        final SenderKeySenderHandler senderKeySender = (distId, recipients, unidentifiedAccess, groupSendEndorsements, isRecipientUpdate) -> messageSender.sendGroupDataMessage(
                distId,
                recipients,
                unidentifiedAccess,
                groupSendEndorsements,
                isRecipientUpdate,
                contentHint,
                message,
                SignalServiceMessageSender.SenderKeyGroupEvents.EMPTY,
                urgent,
                false,
                editTargetTimestamp.map(timestamp -> new SignalServiceEditMessage(timestamp, message)).orElse(null),
                sendResult -> {
                    logger.trace("Partial message send results: {}", sendResult.size());
                    synchronized (entryId) {
                        if (entryId.get() == -1) {
                            final var newId = messageSendLogStore.insertIfPossible(message.getTimestamp(),
                                    sendResult,
                                    contentHint,
                                    urgent);
                            entryId.set(newId);
                        } else {
                            messageSendLogStore.addRecipientToExistingEntryIfPossible(entryId.get(), sendResult);
                        }
                    }
                    synchronized (entryId) {
                        if (entryId.get() == -1) {
                            final var newId = messageSendLogStore.insertIfPossible(message.getTimestamp(),
                                    sendResult,
                                    contentHint,
                                    urgent);
                            entryId.set(newId);
                        } else {
                            messageSendLogStore.addRecipientToExistingEntryIfPossible(entryId.get(), sendResult);
                        }
                    }
                });
        final var results = sendGroupMessageInternal(legacySender, senderKeySender, recipientIds, distributionId);

        for (var r : results) {
            handleSendMessageResult(r);
        }

        return results;
    }

    private List<SendMessageResult> sendGroupTypingMessage(
            final SignalServiceTypingMessage message,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId
    ) throws IOException {
        final var messageSender = dependencies.getMessageSender();
        final var results = sendGroupMessageInternal((recipients, unidentifiedAccess, isRecipientUpdate) -> messageSender.sendTyping(
                        recipients,
                        unidentifiedAccess,
                        message,
                        () -> false),
                (distId, recipients, unidentifiedAccess, groupSendEndorsements, isRecipientUpdate) -> messageSender.sendGroupTyping(
                        distId,
                        recipients,
                        unidentifiedAccess,
                        groupSendEndorsements,
                        message),
                recipientIds,
                distributionId);

        for (var r : results) {
            handleSendMessageResult(r);
        }

        return results;
    }

    private GroupInfo getGroupForSending(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = context.getGroupHelper().getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        if (!g.isProfileSharingEnabled()) {
            g.setProfileSharingEnabled(true);
            account.getGroupStore().updateGroup(g);
        }
        return g;
    }

    private List<SendMessageResult> sendStoryMessageInternal(
            final SenderKeySenderHandler senderKeySender,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId
    ) throws IOException {
        long startTime = System.currentTimeMillis();
        Set<RecipientId> senderKeyTargets = distributionId == null
                ? Set.of()
                : getSenderKeyCapableRecipientIds(recipientIds);
        final var allResults = new ArrayList<SendMessageResult>();

        if (!senderKeyTargets.isEmpty()) {
            final var results = sendGroupMessageInternalWithSenderKey(senderKeySender,
                    senderKeyTargets,
                    distributionId,
                    false);

            if (results == null) {
                senderKeyTargets = Set.of();
            } else {
                results.stream().filter(SendMessageResult::isSuccess).forEach(allResults::add);
                final var recipientResolver = account.getRecipientResolver();
                final var failedTargets = results.stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> recipientResolver.resolveRecipient(r.getAddress()))
                        .toList();
                if (!failedTargets.isEmpty()) {
                    senderKeyTargets = new HashSet<>(senderKeyTargets);
                    failedTargets.forEach(senderKeyTargets::remove);
                }
            }
        }

        final var duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
        logger.debug("Sending took {}", duration.toString());
        return allResults;
    }

    private List<SendMessageResult> sendGroupMessageInternal(
            final LegacySenderHandler legacySender,
            final SenderKeySenderHandler senderKeySender,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId
    ) throws IOException {
        long startTime = System.currentTimeMillis();
        // isRecipientUpdate is true if we've already sent this message to some recipients in the past, otherwise false.
        final var isRecipientUpdate = false;
        Set<RecipientId> senderKeyTargets = distributionId == null
                ? Set.of()
                : getSenderKeyCapableRecipientIds(recipientIds);
        final var allResults = new ArrayList<SendMessageResult>(recipientIds.size());

        if (!senderKeyTargets.isEmpty()) {
            final var results = sendGroupMessageInternalWithSenderKey(senderKeySender,
                    senderKeyTargets,
                    distributionId,
                    isRecipientUpdate);

            if (results == null) {
                senderKeyTargets = Set.of();
            } else {
                results.stream().filter(SendMessageResult::isSuccess).forEach(allResults::add);
                final var recipientResolver = account.getRecipientResolver();
                final var failedTargets = results.stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> recipientResolver.resolveRecipient(r.getAddress()))
                        .toList();
                if (!failedTargets.isEmpty()) {
                    senderKeyTargets = new HashSet<>(senderKeyTargets);
                    failedTargets.forEach(senderKeyTargets::remove);
                }
            }
        }

        final var legacyTargets = new HashSet<>(recipientIds);
        legacyTargets.removeAll(senderKeyTargets);
        final boolean onlyTargetIsSelfWithLinkedDevice = recipientIds.isEmpty() && account.isMultiDevice();

        if (!legacyTargets.isEmpty() || onlyTargetIsSelfWithLinkedDevice) {
            if (!legacyTargets.isEmpty()) {
                logger.debug("Need to do {} legacy sends.", legacyTargets.size());
            } else {
                logger.debug("Need to do a legacy send to send a sync message for a group of only ourselves.");
            }

            final List<SendMessageResult> results = sendGroupMessageInternalWithLegacy(legacySender,
                    legacyTargets,
                    isRecipientUpdate || !allResults.isEmpty());
            allResults.addAll(results);
        }
        final var duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
        logger.debug("Sending took {}", duration.toString());
        return allResults;
    }

    private Set<RecipientId> getSenderKeyCapableRecipientIds(final Set<RecipientId> recipientIds) {
        final var senderKeyTargets = new HashSet<RecipientId>();
        final var recipientList = new ArrayList<>(recipientIds);
        for (final var recipientId : recipientList) {
            final var access = context.getUnidentifiedAccessHelper().getSealedSenderAccessFor(recipientId);
            if (access == null) {
                continue;
            }

            final var serviceId = account.getRecipientAddressResolver()
                    .resolveRecipientAddress(recipientId)
                    .serviceId()
                    .orElse(null);
            if (serviceId == null) {
                continue;
            }
            final var identity = account.getIdentityKeyStore().getIdentityInfo(serviceId);
            if (identity == null || !identity.getTrustLevel().isTrusted()) {
                continue;
            }

            senderKeyTargets.add(recipientId);
        }
/*
        if (senderKeyTargets.size() < 2) {
            logger.debug("Too few sender-key-capable users ({}). Doing all legacy sends.", senderKeyTargets.size());
            return Set.of();
        }
*/
        logger.debug("Can use sender key for {}/{} recipients.", senderKeyTargets.size(), recipientIds.size());
        return senderKeyTargets;
    }

    private List<SendMessageResult> sendGroupMessageInternalWithLegacy(
            final LegacySenderHandler sender,
            final Set<RecipientId> recipientIds,
            final boolean isRecipientUpdate
    ) throws IOException {
        final var recipientIdList = new ArrayList<>(recipientIds);
        final var addresses = recipientIdList.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .toList();
        final var unidentifiedAccesses = context.getUnidentifiedAccessHelper()
                .getSealedSenderAccessFor(recipientIdList);
        try {
            final var results = sender.send(addresses, unidentifiedAccesses, isRecipientUpdate);

            final var successCount = results.stream().filter(SendMessageResult::isSuccess).count();
            logger.debug("Successfully sent using 1:1 to {}/{} legacy targets.", successCount, recipientIdList.size());
            return results;
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return List.of();
        }
    }

    private List<SendMessageResult> sendGroupMessageInternalWithSenderKey(
            final SenderKeySenderHandler sender,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId,
            final boolean isRecipientUpdate
    ) throws IOException {
        final var recipientIdList = new ArrayList<>(recipientIds);

        long keyCreateTime = account.getSenderKeyStore()
                .getCreateTimeForOurKey(account.getAci(), account.getDeviceId(), distributionId);
        long keyAge = System.currentTimeMillis() - keyCreateTime;

        if (keyCreateTime != -1 && keyAge > TimeUnit.DAYS.toMillis(14)) {
            logger.debug("DistributionId {} was created at {} and is {} ms old (~{} days). Rotating.",
                    distributionId,
                    keyCreateTime,
                    keyAge,
                    TimeUnit.MILLISECONDS.toDays(keyAge));
            account.getSenderKeyStore().deleteOurKey(account.getAci(), distributionId);
        }

        List<SignalServiceAddress> addresses = recipientIdList.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .toList();
        List<UnidentifiedAccess> unidentifiedAccesses = context.getUnidentifiedAccessHelper()
                .getAccessFor(recipientIdList)
                .stream()
                .toList();

        final GroupSendEndorsements groupSendEndorsements = null;//TODO
        try {
            List<SendMessageResult> results = sender.send(distributionId,
                    addresses,
                    unidentifiedAccesses,
                    groupSendEndorsements,
                    isRecipientUpdate);

            final var successCount = results.stream().filter(SendMessageResult::isSuccess).count();
            logger.debug("Successfully sent using sender key to {}/{} sender key targets.",
                    successCount,
                    addresses.size());

            return results;
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return null;
        } catch (InvalidUnidentifiedAccessHeaderException e) {
            logger.warn("Someone had a bad UD header. Falling back to legacy sends.", e);
            return null;
        } catch (NoSessionException e) {
            logger.warn("No session. Falling back to legacy sends.", e);
            account.getSenderKeyStore().deleteOurKey(account.getAci(), distributionId);
            return null;
        } catch (InvalidKeyException e) {
            logger.warn("Invalid key. Falling back to legacy sends.", e);
            account.getSenderKeyStore().deleteOurKey(account.getAci(), distributionId);
            return null;
        } catch (InvalidRegistrationIdException e) {
            logger.warn("Invalid registrationId. Falling back to legacy sends.", e);
            return null;
        } catch (NotFoundException e) {
            logger.warn("Someone was unregistered. Falling back to legacy sends.", e);
            return null;
        } catch (IOException e) {
            if (e.getCause() instanceof InvalidKeyException) {
                logger.warn("Invalid key. Falling back to legacy sends.", e);
                return null;
            } else {
                throw e;
            }
        }
    }

    private SendMessageResult sendMessage(
            SignalServiceDataMessage message,
            RecipientId recipientId,
            Optional<Long> editTargetTimestamp
    ) {
        final var messageSendLogStore = account.getMessageSendLogStore();
        final var urgent = true;
        final var result = handleSendMessage(recipientId,
                editTargetTimestamp.isEmpty()
                        ? (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendDataMessage(
                        address,
                        unidentifiedAccess,
                        ContentHint.RESENDABLE,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY,
                        urgent,
                        includePniSignature)
                        : (messageSender, address, unidentifiedAccess, includePniSignature) -> messageSender.sendEditMessage(
                                address,
                                unidentifiedAccess,
                                ContentHint.RESENDABLE,
                                message,
                                SignalServiceMessageSender.IndividualSendEvents.EMPTY,
                                urgent,
                                editTargetTimestamp.get()));
        messageSendLogStore.insertIfPossible(message.getTimestamp(), result, ContentHint.RESENDABLE, urgent);
        handleSendMessageResult(result);
        return result;
    }

    private SendMessageResult handleSendMessage(RecipientId recipientId, SenderHandler s) {
        var messageSender = dependencies.getMessageSender();

        var address = context.getRecipientHelper().resolveSignalServiceAddress(recipientId);
        try {
            final boolean includePniSignature = account.getRecipientStore().needsPniSignature(recipientId);
            try {
                return s.send(messageSender,
                        address,
                        context.getUnidentifiedAccessHelper().getSealedSenderAccessFor(recipientId),
                        includePniSignature);
            } catch (UnregisteredUserException e) {
                final RecipientId newRecipientId;
                try {
                    newRecipientId = context.getRecipientHelper().refreshRegisteredUser(recipientId);
                } catch (UnregisteredRecipientException ex) {
                    return SendMessageResult.unregisteredFailure(address);
                }
                address = context.getRecipientHelper().resolveSignalServiceAddress(newRecipientId);
                return s.send(messageSender,
                        address,
                        context.getUnidentifiedAccessHelper().getSealedSenderAccessFor(newRecipientId),
                        includePniSignature);
            }
        } catch (Throwable e) {
            try {
                return SignalServiceMessageSender.mapSendErrorToSendResult(e, System.currentTimeMillis(), address);
            } catch (IOException ex) {
                logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
                logger.debug("Exception", e);
                return SendMessageResult.networkFailure(address);
            }
        }
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message, Optional<Long> editTargetTimestamp) {
        var address = account.getSelfAddress();
        var transcript = new SentTranscriptMessage(Optional.of(address),
                message.getTimestamp(),
                editTargetTimestamp.isEmpty() ? Optional.of(message) : Optional.empty(),
                message.getExpiresInSeconds(),
                Map.of(address.getServiceId(), true),
                false,
                Optional.empty(),
                Set.of(),
                editTargetTimestamp.map((timestamp) -> new SignalServiceEditMessage(timestamp, message)));
        var syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        return sendSyncMessage(syncMessage);
    }

    private void handleSendMessageResult(final SendMessageResult r) {
        if (r.isSuccess() && !r.getSuccess().isUnidentified()) {
            final var recipientId = account.getRecipientResolver().resolveRecipient(r.getAddress());
            final var profile = account.getProfileStore().getProfile(recipientId);
            if (profile != null && (
                    profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.ENABLED
                            || profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED
            )) {
                account.getProfileStore()
                        .storeProfile(recipientId,
                                Profile.newBuilder(profile)
                                        .withUnidentifiedAccessMode(Profile.UnidentifiedAccessMode.UNKNOWN)
                                        .build());
            }
        }
        if (r.isUnregisteredFailure()) {
            final var recipientId = account.getRecipientResolver().resolveRecipient(r.getAddress());
            final var profile = account.getProfileStore().getProfile(recipientId);
            if (profile != null && (
                    profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.ENABLED
                            || profile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED
            )) {
                account.getProfileStore()
                        .storeProfile(recipientId,
                                Profile.newBuilder(profile)
                                        .withUnidentifiedAccessMode(Profile.UnidentifiedAccessMode.UNKNOWN)
                                        .build());
            }
        }
        if (r.getIdentityFailure() != null) {
            final var recipientId = account.getRecipientResolver().resolveRecipient(r.getAddress());
            context.getIdentityHelper()
                    .handleIdentityFailure(recipientId, r.getAddress().getServiceId(), r.getIdentityFailure());
        }
    }

    interface SenderHandler {

        SendMessageResult send(
                SignalServiceMessageSender messageSender,
                SignalServiceAddress address,
                @Nullable SealedSenderAccess unidentifiedAccess,
                boolean includePniSignature
        ) throws IOException, UnregisteredUserException, ProofRequiredException, RateLimitException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
    }

    interface SenderKeySenderHandler {

        List<SendMessageResult> send(
                DistributionId distributionId,
                List<SignalServiceAddress> recipients,
                List<UnidentifiedAccess> unidentifiedAccess,
                GroupSendEndorsements groupSendEndorsements,
                boolean isRecipientUpdate
        ) throws IOException, UntrustedIdentityException, NoSessionException, InvalidKeyException, InvalidRegistrationIdException;
    }

    interface LegacySenderHandler {

        List<SendMessageResult> send(
                List<SignalServiceAddress> recipients,
                List<SealedSenderAccess> unidentifiedAccess,
                boolean isRecipientUpdate
        ) throws IOException, UntrustedIdentityException;
    }
}
