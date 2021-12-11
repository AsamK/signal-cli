package org.asamk.signal.manager;

import org.asamk.signal.manager.api.Configuration;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendGroupMessageResults;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public interface Manager extends Closeable {

    static Manager init(
            String number,
            File settingsPath,
            ServiceEnvironment serviceEnvironment,
            String userAgent,
            TrustNewIdentity trustNewIdentity
    ) throws IOException, NotRegisteredException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        if (!SignalAccount.userExists(pathConfig.dataPath(), number)) {
            throw new NotRegisteredException();
        }

        var account = SignalAccount.load(pathConfig.dataPath(), number, true, trustNewIdentity);

        if (!account.isRegistered()) {
            account.close();
            throw new NotRegisteredException();
        }

        final var serviceEnvironmentConfig = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);

        return new ManagerImpl(account, pathConfig, serviceEnvironmentConfig, userAgent);
    }

    static void initLogger() {
        LibSignalLogger.initLogger();
    }

    static boolean isValidNumber(final String e164Number, final String countryCode) {
        return PhoneNumberFormatter.isValidNumber(e164Number, countryCode);
    }

    static List<String> getAllLocalAccountNumbers(File settingsPath) {
        var pathConfig = PathConfig.createDefault(settingsPath);
        final var dataPath = pathConfig.dataPath();
        final var files = dataPath.listFiles();

        if (files == null) {
            return List.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .toList();
    }

    String getSelfNumber();

    void checkAccountState() throws IOException;

    Map<String, Pair<String, UUID>> areUsersRegistered(Set<String> numbers) throws IOException;

    void updateAccountAttributes(String deviceName) throws IOException;

    Configuration getConfiguration();

    void updateConfiguration(Configuration configuration) throws IOException, NotMasterDeviceException;

    void setProfile(
            String givenName, String familyName, String about, String aboutEmoji, Optional<File> avatar
    ) throws IOException;

    void unregister() throws IOException;

    void deleteAccount() throws IOException;

    void submitRateLimitRecaptchaChallenge(String challenge, String captcha) throws IOException;

    List<Device> getLinkedDevices() throws IOException;

    void removeLinkedDevices(long deviceId) throws IOException;

    void addDeviceLink(URI linkUri) throws IOException, InvalidDeviceLinkException;

    void setRegistrationLockPin(Optional<String> pin) throws IOException;

    Profile getRecipientProfile(RecipientIdentifier.Single recipient) throws IOException;

    List<Group> getGroups();

    SendGroupMessageResults quitGroup(
            GroupId groupId, Set<RecipientIdentifier.Single> groupAdmins
    ) throws GroupNotFoundException, IOException, NotAGroupMemberException, LastGroupAdminException;

    void deleteGroup(GroupId groupId) throws IOException;

    Pair<GroupId, SendGroupMessageResults> createGroup(
            String name, Set<RecipientIdentifier.Single> members, File avatarFile
    ) throws IOException, AttachmentInvalidException;

    SendGroupMessageResults updateGroup(
            final GroupId groupId, final UpdateGroup updateGroup
    ) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, GroupSendingNotAllowedException;

    Pair<GroupId, SendGroupMessageResults> joinGroup(
            GroupInviteLinkUrl inviteLinkUrl
    ) throws IOException, InactiveGroupLinkException;

    SendMessageResults sendTypingMessage(
            TypingAction action, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendReadReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException;

    SendMessageResults sendViewedReceipt(
            RecipientIdentifier.Single sender, List<Long> messageIds
    ) throws IOException;

    SendMessageResults sendMessage(
            Message message, Set<RecipientIdentifier> recipients
    ) throws IOException, AttachmentInvalidException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendRemoteDeleteMessage(
            long targetSentTimestamp, Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendMessageReaction(
            String emoji,
            boolean remove,
            RecipientIdentifier.Single targetAuthor,
            long targetSentTimestamp,
            Set<RecipientIdentifier> recipients
    ) throws IOException, NotAGroupMemberException, GroupNotFoundException, GroupSendingNotAllowedException;

    SendMessageResults sendEndSessionMessage(Set<RecipientIdentifier.Single> recipients) throws IOException;

    void deleteRecipient(RecipientIdentifier.Single recipient) throws IOException;

    void deleteContact(RecipientIdentifier.Single recipient) throws IOException;

    void setContactName(
            RecipientIdentifier.Single recipient, String name
    ) throws NotMasterDeviceException, IOException;

    void setContactBlocked(
            RecipientIdentifier.Single recipient, boolean blocked
    ) throws NotMasterDeviceException, IOException;

    void setGroupBlocked(
            GroupId groupId, boolean blocked
    ) throws GroupNotFoundException, IOException, NotMasterDeviceException;

    void setExpirationTimer(
            RecipientIdentifier.Single recipient, int messageExpirationTimer
    ) throws IOException;

    URI uploadStickerPack(File path) throws IOException, StickerPackInvalidException;

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
    void receiveMessages(long timeout, TimeUnit unit, ReceiveMessageHandler handler) throws IOException;

    /**
     * Receive new messages from server, returns only if the thread is interrupted.
     */
    void receiveMessages(ReceiveMessageHandler handler) throws IOException;

    void setIgnoreAttachments(boolean ignoreAttachments);

    boolean hasCaughtUpWithOldMessages();

    boolean isContactBlocked(RecipientIdentifier.Single recipient);

    void sendContacts() throws IOException;

    List<Pair<RecipientAddress, Contact>> getContacts();

    String getContactOrProfileName(RecipientIdentifier.Single recipient);

    Group getGroup(GroupId groupId);

    List<Identity> getIdentities();

    List<Identity> getIdentities(RecipientIdentifier.Single recipient);

    boolean trustIdentityVerified(RecipientIdentifier.Single recipient, byte[] fingerprint);

    boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, String safetyNumber);

    boolean trustIdentityVerifiedSafetyNumber(RecipientIdentifier.Single recipient, byte[] safetyNumber);

    boolean trustIdentityAllKeys(RecipientIdentifier.Single recipient);

    void addClosedListener(Runnable listener);

    @Override
    void close() throws IOException;

    interface ReceiveMessageHandler {

        ReceiveMessageHandler EMPTY = (envelope, e) -> {
        };

        void handleMessage(MessageEnvelope envelope, Throwable e);
    }
}
