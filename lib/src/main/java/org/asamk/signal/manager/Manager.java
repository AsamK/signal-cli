package org.asamk.signal.manager;

import org.asamk.signal.manager.api.AlreadyReceivingException;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.IdentityVerificationCode;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.InvalidUsernameException;
import org.asamk.signal.manager.api.LastGroupAdminException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PendingAdminApprovalException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.Recipient;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPack;
import org.asamk.signal.manager.api.StickerPackInvalidException;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Manager extends Closeable {

    static boolean isValidNumber(final String e164Number, final String countryCode) {
        return PhoneNumberFormatter.isValidNumber(e164Number, countryCode);
    }

    static boolean isSignalClientAvailable() {
        final Logger logger = LoggerFactory.getLogger(Manager.class);
        try {
            try {
                org.signal.libsignal.internal.Native.UuidCiphertext_CheckValidContents(new byte[0]);
            } catch (Exception e) {
                logger.trace("Expected exception when checking libsignal-client: {}", e.getMessage());
            }
            return true;
        } catch (UnsatisfiedLinkError e) {
            logger.warn("Failed to call libsignal-client: {}", e.getMessage());
            return false;
        }
    }

    String getSelfNumber();

    /**
     * This is used for checking a set of phone numbers for registration on Signal
     *
     * @param numbers The set of phone number in question
     * @return A map of numbers to canonicalized number and uuid. If a number is not registered the uuid is null.
     * @throws IOException if it's unable to get the contacts to check if they're registered
     */
    Map<String, UserStatus> getUserStatus(Set<String> numbers) throws IOException, RateLimitException;

    void updateAccountAttributes(String deviceName) throws IOException;

    Configuration getConfiguration();

    void updateConfiguration(Configuration configuration) throws NotPrimaryDeviceException;

    /**
     * Update the user's profile.
     * If a field is null, the previous value will be kept.
     */
    void updateProfile(UpdateProfile updateProfile) throws IOException;

    /**
     * Set a username for the account.
     * If the username is null, it will be deleted.
     */
    String setUsername(String username) throws IOException, InvalidUsernameException;

    /**
     * Set a username for the account.
     * If the username is null, it will be deleted.
     */
    void deleteUsername() throws IOException;

    void startChangeNumber(
            String newNumber, boolean voiceVerification, String captcha
    ) throws RateLimitException, IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException, NotPrimaryDeviceException;

    void finishChangeNumber(
            String newNumber, String verificationCode, String pin
    ) throws IncorrectPinException, PinLockedException, IOException, NotPrimaryDeviceException;

    void unregister() throws IOException;

    void deleteAccount() throws IOException;

    void submitRateLimitRecaptchaChallenge(String challenge, String captcha) throws IOException;

    List<Device> getLinkedDevices() throws IOException;

    void removeLinkedDevices(int deviceId) throws IOException;

    void addDeviceLink(DeviceLinkUrl linkUri) throws IOException, InvalidDeviceLinkException, NotPrimaryDeviceException;

    void setRegistrationLockPin(Optional<String> pin) throws IOException, NotPrimaryDeviceException;

    List<Group> getGroups();

    SendGroupMessageResults quitGroup(
            GroupId groupId, Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException, UnregisteredRecipientException;

    void deleteGroup(GroupId groupId) throws IOException;

    Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, String avatarFile
    ) throws IOException, AttachmentInvalidException, UnregisteredRecipientException;

    SendGroupMessageResults updateGroup(
            final GroupId groupId, final UpdateGroup updateGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException, UnregisteredRecipientException;

    Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, InactiveGroupLinkException, PendingAdminApprovalException;

    SendMessageResults sendTypingMessage(
            TypingAction action, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendReadReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    );

    SendMessageResults sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    );

    SendMessageResults sendMessage(
            Message message, Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException, InvalidStickerException;

    SendMessageResults sendEditMessage(
            Message message, Set<RecipientIdentifier> recipients, long editTargetTimestamp
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException, InvalidStickerException;

    SendMessageResults sendRemoteDeleteMessage(
            long targetSentTimestamp, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendMessageReaction(
            String emoji,
            boolean remove,
            RecipientIdentifier.Single targetAuthor,
            long targetSentTimestamp,
            Set<RecipientIdentifier> recipients,
            final boolean isStory
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException, UnregisteredRecipientException;

    SendMessageResults sendPaymentNotificationMessage(
            byte[] receipt, String note, RecipientIdentifier.Single recipient
    ) throws IOException;

    SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) throws IOException;

    void deleteRecipient(RecipientIdentifier.Single recipient);

    void deleteContact(RecipientIdentifier.Single recipient);

    void setContactName(
            RecipientIdentifier.Single recipient, String givenName, final String familyName
    ) throws NotPrimaryDeviceException, UnregisteredRecipientException;

    void setContactsBlocked(
            Collection<RecipientIdentifier.Single> recipient, boolean blocked
    ) throws NotPrimaryDeviceException, IOException, UnregisteredRecipientException;

    void setGroupsBlocked(
            Collection<GroupId> groupId, boolean blocked
    ) throws GroupNotFoundException, IOException, NotPrimaryDeviceException;

    /**
     * Change the expiration timer for a contact
     */
    void setExpirationTimer(
            RecipientIdentifier.Single recipient, int messageExpirationTimer
    ) throws IOException, UnregisteredRecipientException;

    /**
     * Upload the sticker pack from path.
     *
     * @param path Path can be a path to a manifest.json file or to a zip file that contains a manifest.json file
     * @return if successful, returns the URL to install the sticker pack in the signal app
     */
    StickerPackUrl uploadStickerPack(File path) throws IOException, StickerPackInvalidException;

    void installStickerPack(StickerPackUrl url) throws IOException;

    List<StickerPack> getStickerPacks();

    void requestAllSyncData() throws IOException;

    /**
     * Add a handler to receive new messages.
     * Will start receiving messages from server, if not already started.
     */
    default void addReceiveHandler(ReceiveMessageHandler handler) {
        addReceiveHandler(handler, false);
    }

    void addReceiveHandler(ReceiveMessageHandler handler, final boolean isWeakListener);

    /**
     * Remove a handler to receive new messages.
     * Will stop receiving messages from server, if this was the last registered receiver.
     */
    void removeReceiveHandler(ReceiveMessageHandler handler);

    boolean isReceiving();

    /**
     * Receive new messages from server, returns if no new message arrive in a timespan of timeout.
     */
    void receiveMessages(
            Optional<Duration> timeout, Optional<Integer> maxMessages, ReceiveMessageHandler handler
    ) throws IOException, AlreadyReceivingException;

    void setReceiveConfig(ReceiveConfig receiveConfig);

    boolean isContactBlocked(RecipientIdentifier.Single recipient);

    void sendContacts() throws IOException;

    List<Recipient> getRecipients(
            boolean onlyContacts,
            Optional<Boolean> blocked,
            Collection<RecipientIdentifier.Single> address,
            Optional<String> name
    );

    String getContactOrProfileName(RecipientIdentifier.Single recipient);

    Group getGroup(GroupId groupId);

    List<Identity> getIdentities();

    List<Identity> getIdentities(RecipientIdentifier.Single recipient);

    /**
     * Trust this the identity with this fingerprint/safetyNumber
     *
     * @param recipient account of the identity
     */
    boolean trustIdentityVerified(
            RecipientIdentifier.Single recipient, IdentityVerificationCode verificationCode
    ) throws UnregisteredRecipientException;

    /**
     * Trust all keys of this identity without verification
     *
     * @param recipient account of the identity
     */
    boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient) throws UnregisteredRecipientException;

    void addAddressChangedListener(Runnable listener);

    void addClosedListener(Runnable listener);

    InputStream retrieveAttachment(final String id) throws IOException;

    @Override
    void close();

    interface ReceiveMessageHandler {

        ReceiveMessageHandler EMPTY = (envelope, e) -> {
        };

        void handleMessage(MessageEnvelope envelope, Throwable e);
    }
}
