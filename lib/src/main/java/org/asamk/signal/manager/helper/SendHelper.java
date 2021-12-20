package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidRegistrationIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SendHelper {

    private final static Logger logger = LoggerFactory.getLogger(SendHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final UnidentifiedAccessHelper unidentifiedAccessHelper;
    private final SignalServiceAddressResolver addressResolver;
    private final RecipientResolver recipientResolver;
    private final IdentityFailureHandler identityFailureHandler;
    private final GroupProvider groupProvider;
    private final ProfileProvider profileProvider;
    private final RecipientRegistrationRefresher recipientRegistrationRefresher;

    public SendHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final UnidentifiedAccessHelper unidentifiedAccessHelper,
            final SignalServiceAddressResolver addressResolver,
            final RecipientResolver recipientResolver,
            final IdentityFailureHandler identityFailureHandler,
            final GroupProvider groupProvider,
            final ProfileProvider profileProvider,
            final RecipientRegistrationRefresher recipientRegistrationRefresher
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.unidentifiedAccessHelper = unidentifiedAccessHelper;
        this.addressResolver = addressResolver;
        this.recipientResolver = recipientResolver;
        this.identityFailureHandler = identityFailureHandler;
        this.groupProvider = groupProvider;
        this.profileProvider = profileProvider;
        this.recipientRegistrationRefresher = recipientRegistrationRefresher;
    }

    /**
     * Send a single message to one recipient.
     * The message is extended with the current expiration timer.
     */
    public SendMessageResult sendMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final RecipientId recipientId
    ) throws IOException {
        final var contact = account.getContactStore().getContact(recipientId);
        final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
        messageBuilder.withExpiration(expirationTime);
        messageBuilder.withProfileKey(account.getProfileKey().serialize());

        final var message = messageBuilder.build();
        final var result = sendMessage(message, recipientId);
        handleSendMessageResult(result);
        return result;
    }

    /**
     * Send a group message to the given group
     * The message is extended with the current expiration timer for the group and the group context.
     */
    public List<SendMessageResult> sendAsGroupMessage(
            SignalServiceDataMessage.Builder messageBuilder, GroupId groupId
    ) throws IOException, GroupNotFoundException, NotAGroupMemberException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        return sendAsGroupMessage(messageBuilder, g);
    }

    private List<SendMessageResult> sendAsGroupMessage(
            final SignalServiceDataMessage.Builder messageBuilder, final GroupInfo g
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

        return sendGroupMessage(message, recipients, g.getDistributionId());
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
        List<SendMessageResult> result = sendGroupMessageInternal(message, recipientIds, distributionId);

        for (var r : result) {
            handleSendMessageResult(r);
        }

        return result;
    }

    public SendMessageResult sendDeliveryReceipt(
            RecipientId recipientId, List<Long> messageIds
    ) {
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                messageIds,
                System.currentTimeMillis());

        return sendReceiptMessage(receiptMessage, recipientId);
    }

    public SendMessageResult sendReceiptMessage(
            final SignalServiceReceiptMessage receiptMessage, final RecipientId recipientId
    ) {
        return handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendReceipt(address,
                        unidentifiedAccess,
                        receiptMessage));
    }

    public SendMessageResult sendRetryReceipt(
            DecryptionErrorMessage errorMessage, RecipientId recipientId, Optional<GroupId> groupId
    ) {
        logger.debug("Sending retry receipt for {} to {}, device: {}",
                errorMessage.getTimestamp(),
                recipientId,
                errorMessage.getDeviceId());
        return handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendRetryReceipt(address,
                        unidentifiedAccess,
                        groupId.transform(GroupId::serialize),
                        errorMessage));
    }

    public SendMessageResult sendNullMessage(RecipientId recipientId) {
        return handleSendMessage(recipientId, SignalServiceMessageSender::sendNullMessage);
    }

    public SendMessageResult sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder
    ) {
        final var recipientId = account.getSelfRecipientId();
        final var contact = account.getContactStore().getContact(recipientId);
        final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
        messageBuilder.withExpiration(expirationTime);

        var message = messageBuilder.build();
        return sendSelfMessage(message);
    }

    public SendMessageResult sendSyncMessage(SignalServiceSyncMessage message) {
        var messageSender = dependencies.getMessageSender();
        try {
            return messageSender.sendSyncMessage(message, unidentifiedAccessHelper.getAccessForSync());
        } catch (UnregisteredUserException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.unregisteredFailure(address);
        } catch (ProofRequiredException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.proofRequiredFailure(address, e);
        } catch (RateLimitException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            logger.warn("Sending failed due to rate limiting from the signal server: {}", e.getMessage());
            return SendMessageResult.networkFailure(address);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        } catch (IOException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
            return SendMessageResult.networkFailure(address);
        }
    }

    public SendMessageResult sendTypingMessage(
            SignalServiceTypingMessage message, RecipientId recipientId
    ) {
        return handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendTyping(address,
                        unidentifiedAccess,
                        message));
    }

    public List<SendMessageResult> sendGroupTypingMessage(
            SignalServiceTypingMessage message, GroupId groupId
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        if (g.isAnnouncementGroup() && !g.isAdmin(account.getSelfRecipientId())) {
            throw new GroupSendingNotAllowedException(groupId, g.getTitle());
        }
        final var messageSender = dependencies.getMessageSender();
        final var recipientIdList = new ArrayList<>(g.getMembersWithout(account.getSelfRecipientId()));
        final var addresses = recipientIdList.stream().map(addressResolver::resolveSignalServiceAddress).toList();
        return messageSender.sendTyping(addresses,
                unidentifiedAccessHelper.getAccessFor(recipientIdList),
                message,
                null);
    }

    private GroupInfo getGroupForSending(GroupId groupId) throws GroupNotFoundException, NotAGroupMemberException {
        var g = groupProvider.getGroup(groupId);
        if (g == null) {
            throw new GroupNotFoundException(groupId);
        }
        if (!g.isMember(account.getSelfRecipientId())) {
            throw new NotAGroupMemberException(groupId, g.getTitle());
        }
        return g;
    }

    private List<SendMessageResult> sendGroupMessageInternal(
            final SignalServiceDataMessage message,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId
    ) throws IOException {
        // isRecipientUpdate is true if we've already sent this message to some recipients in the past, otherwise false.
        final var isRecipientUpdate = false;
        Set<RecipientId> senderKeyTargets = distributionId == null
                ? Set.of()
                : getSenderKeyCapableRecipientIds(recipientIds);
        final var allResults = new ArrayList<SendMessageResult>(recipientIds.size());

        if (senderKeyTargets.size() > 0) {
            final var results = sendGroupMessageInternalWithSenderKey(message,
                    senderKeyTargets,
                    distributionId,
                    isRecipientUpdate);

            if (results == null) {
                senderKeyTargets = Set.of();
            } else {
                results.stream().filter(SendMessageResult::isSuccess).forEach(allResults::add);
                final var failedTargets = results.stream()
                        .filter(r -> !r.isSuccess())
                        .map(r -> recipientResolver.resolveRecipient(r.getAddress()))
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

            final List<SendMessageResult> results = sendGroupMessageInternalWithLegacy(message,
                    legacyTargets,
                    isRecipientUpdate || allResults.size() > 0);
            allResults.addAll(results);
        }

        return allResults;
    }

    private Set<RecipientId> getSenderKeyCapableRecipientIds(final Set<RecipientId> recipientIds) {
        final var selfProfile = profileProvider.getProfile(account.getSelfRecipientId());
        if (selfProfile == null || !selfProfile.getCapabilities().contains(Profile.Capability.senderKey)) {
            logger.debug("Not all of our devices support sender key. Using legacy.");
            return Set.of();
        }

        final var senderKeyTargets = new HashSet<RecipientId>();
        for (final var recipientId : recipientIds) {
            // TODO filter out unregistered
            final var profile = profileProvider.getProfile(recipientId);
            if (profile == null || !profile.getCapabilities().contains(Profile.Capability.senderKey)) {
                continue;
            }

            final var access = unidentifiedAccessHelper.getAccessFor(recipientId);
            if (!access.isPresent() || !access.get().getTargetUnidentifiedAccess().isPresent()) {
                continue;
            }

            final var identity = account.getIdentityKeyStore().getIdentity(recipientId);
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
            final SignalServiceDataMessage message, final Set<RecipientId> recipientIds, final boolean isRecipientUpdate
    ) throws IOException {
        final var recipientIdList = new ArrayList<>(recipientIds);
        final var addresses = recipientIdList.stream().map(addressResolver::resolveSignalServiceAddress).toList();
        final var unidentifiedAccesses = unidentifiedAccessHelper.getAccessFor(recipientIdList);
        final var messageSender = dependencies.getMessageSender();
        try {
            final var results = messageSender.sendDataMessage(addresses,
                    unidentifiedAccesses,
                    isRecipientUpdate,
                    ContentHint.DEFAULT,
                    message,
                    SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                    sendResult -> logger.trace("Partial message send result: {}", sendResult.isSuccess()),
                    () -> false);

            final var successCount = results.stream().filter(SendMessageResult::isSuccess).count();
            logger.debug("Successfully sent using 1:1 to {}/{} legacy targets.", successCount, recipientIdList.size());
            return results;
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return List.of();
        }
    }

    private List<SendMessageResult> sendGroupMessageInternalWithSenderKey(
            final SignalServiceDataMessage message,
            final Set<RecipientId> recipientIds,
            final DistributionId distributionId,
            final boolean isRecipientUpdate
    ) throws IOException {
        final var recipientIdList = new ArrayList<>(recipientIds);
        final var messageSender = dependencies.getMessageSender();

        long keyCreateTime = account.getSenderKeyStore()
                .getCreateTimeForOurKey(account.getSelfRecipientId(), account.getDeviceId(), distributionId);
        long keyAge = System.currentTimeMillis() - keyCreateTime;

        if (keyCreateTime != -1 && keyAge > TimeUnit.DAYS.toMillis(14)) {
            logger.debug("DistributionId {} was created at {} and is {} ms old (~{} days). Rotating.",
                    distributionId,
                    keyCreateTime,
                    keyAge,
                    TimeUnit.MILLISECONDS.toDays(keyAge));
            account.getSenderKeyStore().deleteOurKey(account.getSelfRecipientId(), distributionId);
        }

        List<SignalServiceAddress> addresses = recipientIdList.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .collect(Collectors.toList());
        List<UnidentifiedAccess> unidentifiedAccesses = recipientIdList.stream()
                .map(unidentifiedAccessHelper::getAccessFor)
                .map(Optional::get)
                .map(UnidentifiedAccessPair::getTargetUnidentifiedAccess)
                .map(Optional::get)
                .collect(Collectors.toList());

        try {
            List<SendMessageResult> results = messageSender.sendGroupDataMessage(distributionId,
                    addresses,
                    unidentifiedAccesses,
                    isRecipientUpdate,
                    ContentHint.DEFAULT,
                    message,
                    SignalServiceMessageSender.SenderKeyGroupEvents.EMPTY);

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
            account.getSenderKeyStore().deleteOurKey(account.getSelfRecipientId(), distributionId);
            return null;
        } catch (InvalidKeyException e) {
            logger.warn("Invalid key. Falling back to legacy sends.", e);
            account.getSenderKeyStore().deleteOurKey(account.getSelfRecipientId(), distributionId);
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
            SignalServiceDataMessage message, RecipientId recipientId
    ) {
        return handleSendMessage(recipientId,
                (messageSender, address, unidentifiedAccess) -> messageSender.sendDataMessage(address,
                        unidentifiedAccess,
                        ContentHint.DEFAULT,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY));
    }

    private SendMessageResult handleSendMessage(RecipientId recipientId, SenderHandler s) {
        var messageSender = dependencies.getMessageSender();

        var address = addressResolver.resolveSignalServiceAddress(recipientId);
        try {
            try {
                return s.send(messageSender, address, unidentifiedAccessHelper.getAccessFor(recipientId));
            } catch (UnregisteredUserException e) {
                final var newRecipientId = recipientRegistrationRefresher.refreshRecipientRegistration(recipientId);
                address = addressResolver.resolveSignalServiceAddress(newRecipientId);
                return s.send(messageSender, address, unidentifiedAccessHelper.getAccessFor(newRecipientId));
            }
        } catch (UnregisteredUserException e) {
            return SendMessageResult.unregisteredFailure(address);
        } catch (ProofRequiredException e) {
            return SendMessageResult.proofRequiredFailure(address, e);
        } catch (RateLimitException e) {
            logger.warn("Sending failed due to rate limiting from the signal server: {}", e.getMessage());
            return SendMessageResult.networkFailure(address);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        } catch (IOException e) {
            logger.warn("Failed to send message due to IO exception: {}", e.getMessage());
            return SendMessageResult.networkFailure(address);
        }
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message) {
        var address = account.getSelfAddress();
        var transcript = new SentTranscriptMessage(Optional.of(address),
                message.getTimestamp(),
                message,
                message.getExpiresInSeconds(),
                Map.of(address, true),
                false);
        var syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        return sendSyncMessage(syncMessage);
    }

    private void handleSendMessageResult(final SendMessageResult r) {
        if (r.getIdentityFailure() != null) {
            final var recipientId = recipientResolver.resolveRecipient(r.getAddress());
            identityFailureHandler.handleIdentityFailure(recipientId, r.getIdentityFailure());
        }
    }

    interface SenderHandler {

        SendMessageResult send(
                SignalServiceMessageSender messageSender,
                SignalServiceAddress address,
                Optional<UnidentifiedAccessPair> unidentifiedAccess
        ) throws IOException, UnregisteredUserException, ProofRequiredException, RateLimitException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
    }
}
