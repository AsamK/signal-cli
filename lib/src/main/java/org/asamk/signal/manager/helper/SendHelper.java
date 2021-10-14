package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final RecipientRegistrationRefresher recipientRegistrationRefresher;

    public SendHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final UnidentifiedAccessHelper unidentifiedAccessHelper,
            final SignalServiceAddressResolver addressResolver,
            final RecipientResolver recipientResolver,
            final IdentityFailureHandler identityFailureHandler,
            final GroupProvider groupProvider,
            final RecipientRegistrationRefresher recipientRegistrationRefresher
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.unidentifiedAccessHelper = unidentifiedAccessHelper;
        this.addressResolver = addressResolver;
        this.recipientResolver = recipientResolver;
        this.identityFailureHandler = identityFailureHandler;
        this.groupProvider = groupProvider;
        this.recipientRegistrationRefresher = recipientRegistrationRefresher;
    }

    /**
     * Send a single message to one or multiple recipients.
     * The message is extended with the current expiration timer for each recipient.
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
        handlePossibleIdentityFailure(result);
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

        return sendGroupMessage(message, recipients);
    }

    /**
     * Send a complete group message to the given recipients (should be current/old/new members)
     * This method should only be used for create/update/quit group messages.
     */
    public List<SendMessageResult> sendGroupMessage(
            final SignalServiceDataMessage message, final Set<RecipientId> recipientIds
    ) throws IOException {
        List<SendMessageResult> result = sendGroupMessageInternal(message, recipientIds);

        for (var r : result) {
            handlePossibleIdentityFailure(r);
        }

        return result;
    }

    public void sendDeliveryReceipt(
            RecipientId recipientId, List<Long> messageIds
    ) throws IOException, UntrustedIdentityException {
        var receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY,
                messageIds,
                System.currentTimeMillis());

        sendReceiptMessage(receiptMessage, recipientId);
    }

    public void sendReceiptMessage(
            final SignalServiceReceiptMessage receiptMessage, final RecipientId recipientId
    ) throws IOException, UntrustedIdentityException {
        final var messageSender = dependencies.getMessageSender();
        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        try {
            messageSender.sendReceipt(address, unidentifiedAccessHelper.getAccessFor(recipientId), receiptMessage);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            throw new UntrustedIdentityException(address);
        }
    }

    public void sendRetryReceipt(
            DecryptionErrorMessage errorMessage, RecipientId recipientId, Optional<GroupId> groupId
    ) throws IOException, UntrustedIdentityException {
        var messageSender = dependencies.getMessageSender();
        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        logger.debug("Sending retry receipt for {} to {}, device: {}",
                errorMessage.getTimestamp(),
                recipientId,
                errorMessage.getDeviceId());
        try {
            messageSender.sendRetryReceipt(address,
                    unidentifiedAccessHelper.getAccessFor(recipientId),
                    groupId.transform(GroupId::serialize),
                    errorMessage);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            throw new UntrustedIdentityException(address);
        }
    }

    public SendMessageResult sendNullMessage(RecipientId recipientId) throws IOException {
        var messageSender = dependencies.getMessageSender();

        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        try {
            try {
                return messageSender.sendNullMessage(address, unidentifiedAccessHelper.getAccessFor(recipientId));
            } catch (UnregisteredUserException e) {
                final var newRecipientId = recipientRegistrationRefresher.refreshRecipientRegistration(recipientId);
                final var newAddress = addressResolver.resolveSignalServiceAddress(newRecipientId);
                return messageSender.sendNullMessage(newAddress, unidentifiedAccessHelper.getAccessFor(newRecipientId));
            }
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    public SendMessageResult sendSelfMessage(
            SignalServiceDataMessage.Builder messageBuilder
    ) throws IOException {
        final var recipientId = account.getSelfRecipientId();
        final var contact = account.getContactStore().getContact(recipientId);
        final var expirationTime = contact != null ? contact.getMessageExpirationTime() : 0;
        messageBuilder.withExpiration(expirationTime);

        var message = messageBuilder.build();
        return sendSelfMessage(message);
    }

    public SendMessageResult sendSyncMessage(SignalServiceSyncMessage message) throws IOException {
        var messageSender = dependencies.getMessageSender();
        try {
            return messageSender.sendSyncMessage(message, unidentifiedAccessHelper.getAccessForSync());
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            var address = addressResolver.resolveSignalServiceAddress(account.getSelfRecipientId());
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    public void sendTypingMessage(
            SignalServiceTypingMessage message, RecipientId recipientId
    ) throws IOException, UntrustedIdentityException {
        var messageSender = dependencies.getMessageSender();
        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        try {
            try {
                messageSender.sendTyping(address, unidentifiedAccessHelper.getAccessFor(recipientId), message);
            } catch (UnregisteredUserException e) {
                final var newRecipientId = recipientRegistrationRefresher.refreshRecipientRegistration(recipientId);
                final var newAddress = addressResolver.resolveSignalServiceAddress(newRecipientId);
                messageSender.sendTyping(newAddress, unidentifiedAccessHelper.getAccessFor(newRecipientId), message);
            }
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            throw new UntrustedIdentityException(address);
        }
    }

    public void sendGroupTypingMessage(
            SignalServiceTypingMessage message, GroupId groupId
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException {
        final var g = getGroupForSending(groupId);
        if (g.isAnnouncementGroup() && !g.isAdmin(account.getSelfRecipientId())) {
            throw new GroupSendingNotAllowedException(groupId, g.getTitle());
        }
        final var messageSender = dependencies.getMessageSender();
        final var recipientIdList = new ArrayList<>(g.getMembersWithout(account.getSelfRecipientId()));
        final var addresses = recipientIdList.stream()
                .map(addressResolver::resolveSignalServiceAddress)
                .collect(Collectors.toList());
        messageSender.sendTyping(addresses, unidentifiedAccessHelper.getAccessFor(recipientIdList), message, null);
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
            final SignalServiceDataMessage message, final Set<RecipientId> recipientIds
    ) throws IOException {
        try {
            var messageSender = dependencies.getMessageSender();
            // isRecipientUpdate is true if we've already sent this message to some recipients in the past, otherwise false.
            final var isRecipientUpdate = false;
            final var recipientIdList = new ArrayList<>(recipientIds);
            final var addresses = recipientIdList.stream()
                    .map(addressResolver::resolveSignalServiceAddress)
                    .collect(Collectors.toList());
            return messageSender.sendDataMessage(addresses,
                    unidentifiedAccessHelper.getAccessFor(recipientIdList),
                    isRecipientUpdate,
                    ContentHint.DEFAULT,
                    message,
                    SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                    sendResult -> logger.trace("Partial message send result: {}", sendResult.isSuccess()),
                    () -> false);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return List.of();
        }
    }

    private SendMessageResult sendMessage(
            SignalServiceDataMessage message, RecipientId recipientId
    ) throws IOException {
        var messageSender = dependencies.getMessageSender();

        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        try {
            try {
                return messageSender.sendDataMessage(address,
                        unidentifiedAccessHelper.getAccessFor(recipientId),
                        ContentHint.DEFAULT,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY);
            } catch (UnregisteredUserException e) {
                final var newRecipientId = recipientRegistrationRefresher.refreshRecipientRegistration(recipientId);
                return messageSender.sendDataMessage(addressResolver.resolveSignalServiceAddress(newRecipientId),
                        unidentifiedAccessHelper.getAccessFor(newRecipientId),
                        ContentHint.DEFAULT,
                        message,
                        SignalServiceMessageSender.IndividualSendEvents.EMPTY);
            }
        } catch (ProofRequiredException e) {
            return SendMessageResult.proofRequiredFailure(address, e);
        } catch (RateLimitException e) {
            logger.warn("Sending failed due to rate limiting from the signal server: {}", e.getMessage());
            return SendMessageResult.networkFailure(address);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            return SendMessageResult.identityFailure(address, e.getIdentityKey());
        }
    }

    private SendMessageResult sendSelfMessage(SignalServiceDataMessage message) throws IOException {
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

    private void handlePossibleIdentityFailure(final SendMessageResult r) {
        if (r.getIdentityFailure() != null) {
            final var recipientId = recipientResolver.resolveRecipient(r.getAddress());
            identityFailureHandler.handleIdentityFailure(recipientId, r.getIdentityFailure());
        }
    }
}
