package org.asamk.signal.manager.helper;

import com.google.protobuf.ByteString;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.sendLog.MessageSendLogEntry;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEditMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
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

public class SendHelper {

    private final static Logger logger = LoggerFactory.getLogger(SendHelper.class);

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
        if (contact == null || !contact.isProfileSharingEnabled()) {
            final var contactBuilder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            contact = contactBuilder.withProfileSharingEnabled(true).build();
            account.getContactStore().storeContact(recipientId, contact);
        }

        final var expirationTime = contact.getMessageExpirationTime();
        messageBuilder.withExpiration(expirationTime);

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
            SignalServiceDataMessage.Builder messageBuilder, GroupId groupId, Optional<Long> editTargetTimestamp
    ) throws IOException, GroupNotFoundException, NotAGroupMemberException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        return sendAsGroupMessage(messageBuilder, g, editTargetTimestamp);
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
            final SignalServiceReceiptMessage receiptMessage, final RecipientId recipientId
    ) {
        final var messageSendLogStore = account.getMessageSendLogStore();
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendReceipt(address,
                        unidentifiedAccess,
                        receiptMessage,
                        false));
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
                (messageSender, address, unidentifiedAccess) -> messageSender.sendDataMessage(address,
                        unidentifiedAccess,
                        ContentHint.IMPLICIT,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY,
                        false,
                        false));
    }

    public SendMessageResult sendRetryReceipt(
            DecryptionErrorMessage errorMessage, RecipientId recipientId, Optional<GroupId> groupId
    ) {
        logger.debug("Sending retry receipt for {} to {}, device: {}",
                errorMessage.getTimestamp(),
                recipientId,
                errorMessage.getDeviceId());
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendRetryReceipt(address,
                        unidentifiedAccess,
                        groupId.map(GroupId::serialize),
                        errorMessage));
        handleSendMessageResult(result);
        return result;
    }

    public SendMessageResult sendNullMessage(RecipientId recipientId) {
        final var result = handleSendMessage(recipientId, SignalServiceMessageSender::sendNullMessage);
        handleSendMessageResult(result);
        return result;
    }

    public SendMessageResult sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder, Optional<Long> editTargetTimestamp
    ) {
        final var recipientId = account.getSelfRecipientId();
        final var contact = account.getContactStore().getContact(recipientId);
        final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
        messageBuilder.withExpiration(expirationTime);

        var message = messageBuilder.build();
        return sendSelfMessage(message, editTargetTimestamp);
    }

    public SendMessageResult sendSyncMessage(SignalServiceSyncMessage message) {
        var messageSender = dependencies.getMessageSender();
        try {
            return messageSender.sendSyncMessage(message, context.getUnidentifiedAccessHelper().getAccessForSync());
        } catch (UnregisteredUserException e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.unregisteredFailure(address);
        } catch (ProofRequiredException e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.proofRequiredFailure(address, e);
        } catch (RateLimitException e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            logger.warn("Sending failed due to rate limiting from the signal server: {}", e.getMessage());
            return SendMessageResult.rateLimitFailure(address, e);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        } catch (IOException e) {
            var address = context.getRecipientHelper().resolveSignalServiceAddress(account.getSelfRecipientId());
            logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
            logger.debug("Exception", e);
            return SendMessageResult.networkFailure(address);
        }
    }

    public SendMessageResult sendTypingMessage(
            SignalServiceTypingMessage message, RecipientId recipientId
    ) {
        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendTyping(List.of(address),
                        List.of(unidentifiedAccess),
                        message,
                        null).get(0));
        handleSendMessageResult(result);
        return result;
    }

    public List<SendMessageResult> sendGroupTypingMessage(
            SignalServiceTypingMessage message, GroupId groupId
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
            final RecipientId recipientId, final long timestamp, final MessageSendLogEntry messageSendLogEntry
    ) {
        logger.trace("Resending message {} to {}", timestamp, recipientId);
        if (messageSendLogEntry.groupId().isEmpty()) {
            return handleSendMessage(recipientId,
                    (messageSender, address, unidentifiedAccess) -> messageSender.resendContent(address,
                            unidentifiedAccess,
                            timestamp,
                            messageSendLogEntry.content(),
                            messageSendLogEntry.contentHint(),
                            Optional.empty(),
                            messageSendLogEntry.urgent()));
        }

        final var groupId = messageSendLogEntry.groupId().get();
        final var group = account.getGroupStore().getGroup(groupId);

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
        final var distributionBytes = ByteString.copyFrom(senderKeyDistributionMessage.serialize());
        final var contentToSend = messageSendLogEntry.content()
                .toBuilder()
                .setSenderKeyDistributionMessage(distributionBytes)
                .build();

        final var result = handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.resendContent(address,
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
            final SignalServiceDataMessage.Builder messageBuilder, final GroupInfo g, Optional<Long> editTargetTimestamp
    ) throws IOException, GroupSendingNotAllowedException {
        GroupUtils.setGroupContext(messageBuilder, g);
        messageBuilder.withExpiration(g.getMessageExpirationTimer());

        final var message = messageBuilder.build();
        final var recipients = g.getMembersWithout(account.getSelfRecipientId());

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
        final LegacySenderHandler legacySender = (recipients, unidentifiedAccess, isRecipientUpdate) -> messageSender.sendDataMessage(
                recipients,
                unidentifiedAccess,
                isRecipientUpdate,
                contentHint,
                message,
                SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                sendResult -> {
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
                },
                () -> false,
                urgent);
        final SenderKeySenderHandler senderKeySender = (distId, recipients, unidentifiedAccess, isRecipientUpdate) -> messageSender.sendGroupDataMessage(
                distId,
                recipients,
                unidentifiedAccess,
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
                (distId, recipients, unidentifiedAccess, isRecipientUpdate) -> messageSender.sendGroupTyping(distId,
                        recipients,
                        unidentifiedAccess,
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
        return g;
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

        if (senderKeyTargets.size() > 0) {
            final var results = sendGroupMessageInternalWithSenderKey(senderKeySender,
                    senderKeyTargets,
                    distributionId,
                    isRecipientUpdate);

            if (results == null) {
                senderKeyTargets = Set.of();
            } else {
                results.stream().filter(SendMessageResult::isSuccess).forEach(allResults::add);
                final var failedTargets = results.stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> context.getRecipientHelper().resolveRecipient(r.getAddress()))
                        .toList();
                if (failedTargets.size() > 0) {
                    senderKeyTargets = new HashSet<>(senderKeyTargets);
                    failedTargets.forEach(senderKeyTargets::remove);
                }
            }
        }

        final var legacyTargets = new HashSet<>(recipientIds);
        legacyTargets.removeAll(senderKeyTargets);
        final boolean onlyTargetIsSelfWithLinkedDevice = recipientIds.isEmpty() && account.isMultiDevice();

        if (legacyTargets.size() > 0 || onlyTargetIsSelfWithLinkedDevice) {
            if (legacyTargets.size() > 0) {
                logger.debug("Need to do {} legacy sends.", legacyTargets.size());
            } else {
                logger.debug("Need to do a legacy send to send a sync message for a group of only ourselves.");
            }

            final List<SendMessageResult> results = sendGroupMessageInternalWithLegacy(legacySender,
                    legacyTargets,
                    isRecipientUpdate || allResults.size() > 0);
            allResults.addAll(results);
        }
        final var duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
        logger.debug("Sending took {}", duration.toString());
        return allResults;
    }

    private Set<RecipientId> getSenderKeyCapableRecipientIds(final Set<RecipientId> recipientIds) {
        final var selfProfile = context.getProfileHelper().getSelfProfile();
        if (selfProfile == null || !selfProfile.getCapabilities().contains(Profile.Capability.senderKey)) {
            logger.debug("Not all of our devices support sender key. Using legacy.");
            return Set.of();
        }

        final var senderKeyTargets = new HashSet<RecipientId>();
        final var recipientList = new ArrayList<>(recipientIds);
        final var profiles = context.getProfileHelper().getRecipientProfiles(recipientList).iterator();
        for (final var recipientId : recipientList) {
            final var profile = profiles.next();
            if (profile == null || !profile.getCapabilities().contains(Profile.Capability.senderKey)) {
                continue;
            }

            final var access = context.getUnidentifiedAccessHelper().getAccessFor(recipientId);
            if (access.isEmpty() || access.get().getTargetUnidentifiedAccess().isEmpty()) {
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

        if (senderKeyTargets.size() < 2) {
            logger.debug("Too few sender-key-capable users ({}). Doing all legacy sends.", senderKeyTargets.size());
            return Set.of();
        }

        logger.debug("Can use sender key for {}/{} recipients.", senderKeyTargets.size(), recipientIds.size());
        return senderKeyTargets;
    }

    private List<SendMessageResult> sendGroupMessageInternalWithLegacy(
            final LegacySenderHandler sender, final Set<RecipientId> recipientIds, final boolean isRecipientUpdate
    ) throws IOException {
        final var recipientIdList = new ArrayList<>(recipientIds);
        final var addresses = recipientIdList.stream()
                .map(context.getRecipientHelper()::resolveSignalServiceAddress)
                .toList();
        final var unidentifiedAccesses = context.getUnidentifiedAccessHelper().getAccessFor(recipientIdList);
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
                .map(Optional::get)
                .map(UnidentifiedAccessPair::getTargetUnidentifiedAccess)
                .map(Optional::get)
                .toList();

        try {
            List<SendMessageResult> results = sender.send(distributionId,
                    addresses,
                    unidentifiedAccesses,
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
        }
    }

    private SendMessageResult sendMessage(
            SignalServiceDataMessage message, RecipientId recipientId, Optional<Long> editTargetTimestamp
    ) {
        final var messageSendLogStore = account.getMessageSendLogStore();
        final var urgent = true;
        final var includePniSignature = false;
        final var result = handleSendMessage(recipientId,
                editTargetTimestamp.isEmpty()
                        ? (messageSender, address, unidentifiedAccess) -> messageSender.sendDataMessage(address,
                        unidentifiedAccess,
                        ContentHint.RESENDABLE,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY,
                        urgent,
                        includePniSignature)
                        : (messageSender, address, unidentifiedAccess) -> messageSender.sendEditMessage(address,
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
            try {
                return s.send(messageSender, address, context.getUnidentifiedAccessHelper().getAccessFor(recipientId));
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
                        context.getUnidentifiedAccessHelper().getAccessFor(newRecipientId));
            }
        } catch (UnregisteredUserException e) {
            return SendMessageResult.unregisteredFailure(address);
        } catch (ProofRequiredException e) {
            return SendMessageResult.proofRequiredFailure(address, e);
        } catch (RateLimitException e) {
            logger.warn("Sending failed due to rate limiting from the signal server: {}", e.getMessage());
            return SendMessageResult.rateLimitFailure(address, e);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        } catch (IOException e) {
            logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
            logger.debug("Exception", e);
            return SendMessageResult.networkFailure(address);
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
            final var recipientId = context.getRecipientHelper().resolveRecipient(r.getAddress());
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
            final var recipientId = context.getRecipientHelper().resolveRecipient(r.getAddress());
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
            final var recipientId = context.getRecipientHelper().resolveRecipient(r.getAddress());
            context.getIdentityHelper()
                    .handleIdentityFailure(recipientId, r.getAddress().getServiceId(), r.getIdentityFailure());
        }
    }

    interface SenderHandler {

        SendMessageResult send(
                SignalServiceMessageSender messageSender,
                SignalServiceAddress address,
                Optional<UnidentifiedAccessPair> unidentifiedAccess
        ) throws IOException, UnregisteredUserException, ProofRequiredException, RateLimitException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
    }

    interface SenderKeySenderHandler {

        List<SendMessageResult> send(
                DistributionId distributionId,
                List<SignalServiceAddress> recipients,
                List<UnidentifiedAccess> unidentifiedAccess,
                boolean isRecipientUpdate
        ) throws IOException, UntrustedIdentityException, NoSessionException, InvalidKeyException, InvalidRegistrationIdException;
    }

    interface LegacySenderHandler {

        List<SendMessageResult> send(
                List<SignalServiceAddress> recipients,
                List<Optional<UnidentifiedAccessPair>> unidentifiedAccess,
                boolean isRecipientUpdate
        ) throws IOException, UntrustedIdentityException;
    }
}
