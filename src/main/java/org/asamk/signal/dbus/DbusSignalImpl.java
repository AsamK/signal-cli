package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.CaptchaRejectedException;
import org.asamk.signal.manager.api.DeviceLimitExceededException;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupInviteLinkUrl;
import org.asamk.signal.manager.api.GroupLinkState;
import org.asamk.signal.manager.api.GroupNotFoundException;
import org.asamk.signal.manager.api.GroupPermission;
import org.asamk.signal.manager.api.GroupSendingNotAllowedException;
import org.asamk.signal.manager.api.IdentityVerificationCode;
import org.asamk.signal.manager.api.InactiveGroupLinkException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.InvalidNumberException;
import org.asamk.signal.manager.api.InvalidStickerException;
import org.asamk.signal.manager.api.LastGroupAdminException;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.NotAGroupMemberException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.PendingAdminApprovalException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.RecipientAddress;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResult;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.manager.api.StickerPackInvalidException;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.SendMessageResultUtils;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.asamk.signal.dbus.DbusUtils.makeValidObjectPathElement;

public class DbusSignalImpl implements Signal, AutoCloseable {

    private final Manager m;
    private final DBusConnection connection;
    private final String objectPath;
    private final boolean noReceiveOnStart;

    private DBusPath thisDevice;
    private final List<StructDevice> devices = new ArrayList<>();
    private final List<StructGroup> groups = new ArrayList<>();
    private final List<StructIdentity> identities = new ArrayList<>();
    private DbusReceiveMessageHandler dbusMessageHandler;
    private int subscriberCount;

    private static final Logger logger = LoggerFactory.getLogger(DbusSignalImpl.class);

    public DbusSignalImpl(
            final Manager m,
            DBusConnection connection,
            final String objectPath,
            final boolean noReceiveOnStart
    ) {
        this.m = m;
        this.connection = connection;
        this.objectPath = objectPath;
        this.noReceiveOnStart = noReceiveOnStart;

        m.addAddressChangedListener(() -> {
            unExportObjects();
            exportObjects();
        });
    }

    public void initObjects() {
        exportObjects();
        if (!noReceiveOnStart) {
            subscribeReceive();
        }
    }

    private void exportObjects() {
        exportObject(this);

        updateDevices();
        updateGroups();
        updateConfiguration();
        updateIdentities();
    }

    @Override
    public void close() {
        if (dbusMessageHandler != null) {
            m.removeReceiveHandler(dbusMessageHandler);
            dbusMessageHandler = null;
        }
        unExportObjects();
    }

    private void unExportObjects() {
        unExportDevices();
        unExportGroups();
        unExportConfiguration();
        unExportIdentities();
        connection.unExportObject(this.objectPath);
    }

    @Override
    public String getObjectPath() {
        return objectPath;
    }

    @Override
    public String getSelfNumber() {
        return m.getSelfNumber();
    }

    @Override
    public void subscribeReceive() {
        if (dbusMessageHandler == null) {
            dbusMessageHandler = new DbusReceiveMessageHandler(connection, objectPath);
            m.addReceiveHandler(dbusMessageHandler);
        }
        subscriberCount++;
    }

    @Override
    public void unsubscribeReceive() {
        subscriberCount = Math.max(0, subscriberCount - 1);
        if (subscriberCount == 0 && dbusMessageHandler != null) {
            m.removeReceiveHandler(dbusMessageHandler);
            dbusMessageHandler = null;
        }
    }

    @Override
    public void submitRateLimitChallenge(String challenge, String captcha) {
        try {
            m.submitRateLimitRecaptchaChallenge(challenge, captcha);
        } catch (IOException e) {
            throw new Error.Failure("Submit challenge error: " + e.getMessage());
        } catch (CaptchaRejectedException e) {
            throw new Error.Failure(
                    "Captcha rejected, it may be outdated, already used or solved from a different IP address.");
        }
    }

    @Override
    public void unregister() throws Error.Failure {
        try {
            m.unregister();
        } catch (IOException e) {
            throw new Error.Failure("Failed to unregister: " + e.getMessage());
        }
    }

    @Override
    public void deleteAccount() throws Error.Failure {
        try {
            m.deleteAccount();
        } catch (IOException e) {
            throw new Error.Failure("Failed to delete account: " + e.getMessage());
        }
    }

    @Override
    public void addDevice(String uri) {
        try {
            var deviceLinkUrl = DeviceLinkUrl.parseDeviceLinkUri(new URI(uri));
            m.addDeviceLink(deviceLinkUrl);
        } catch (IOException | InvalidDeviceLinkException | DeviceLimitExceededException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + " Add device link failed. " + e.getMessage());
        } catch (NotPrimaryDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (URISyntaxException e) {
            throw new Error.InvalidUri(e.getClass().getSimpleName()
                    + " Device link uri has invalid format: "
                    + e.getMessage());
        }
    }

    @Override
    public DBusPath getDevice(long deviceId) {
        updateDevices();
        final var deviceOptional = devices.stream().filter(g -> g.getId().equals(deviceId)).findFirst();
        if (deviceOptional.isEmpty()) {
            throw new Error.DeviceNotFound("Device not found");
        }
        return deviceOptional.get().getObjectPath();
    }

    @Override
    public List<StructDevice> listDevices() {
        updateDevices();
        return this.devices;
    }

    @Override
    public DBusPath getThisDevice() {
        updateDevices();
        return thisDevice;
    }

    @Override
    public long sendMessage(final String message, final List<String> attachments, final String recipient) {
        return sendMessage(message, attachments, List.of(recipient));
    }

    @Override
    public long sendMessage(final String messageText, final List<String> attachments, final List<String> recipients) {
        try {
            final var message = new Message(messageText,
                    attachments,
                    false,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    false);
            final var recipientIdentifiers = getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                    .map(RecipientIdentifier.class::cast)
                    .collect(Collectors.toSet());
            final var results = m.sendMessage(message, recipientIdentifiers, false);

            checkSendMessageResults(results);
            return results.timestamp();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException | InvalidStickerException e) {
            throw new Error.Failure(e);
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public long sendRemoteDeleteMessage(final long targetSentTimestamp, final String recipient) {
        return sendRemoteDeleteMessage(targetSentTimestamp, List.of(recipient));
    }

    @Override
    public long sendRemoteDeleteMessage(final long targetSentTimestamp, final List<String> recipients) {
        try {
            final var results = m.sendRemoteDeleteMessage(targetSentTimestamp,
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendMessageReaction(
            final String emoji,
            final boolean remove,
            final String targetAuthor,
            final long targetSentTimestamp,
            final String recipient
    ) {
        return sendMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, List.of(recipient));
    }

    @Override
    public long sendMessageReaction(
            final String emoji,
            final boolean remove,
            final String targetAuthor,
            final long targetSentTimestamp,
            final List<String> recipients
    ) {
        try {
            final var results = m.sendMessageReaction(emoji,
                    remove,
                    getSingleRecipientIdentifier(targetAuthor, m.getSelfNumber()),
                    targetSentTimestamp,
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()),
                    false,
                    false);
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public long sendPaymentNotification(
            final byte[] receipt,
            final String note,
            final String recipient
    ) throws Error.Failure {
        try {
            final var results = m.sendPaymentNotificationMessage(receipt,
                    note,
                    getSingleRecipientIdentifier(recipient, m.getSelfNumber()));
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void sendTyping(
            final String recipient,
            final boolean stop
    ) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity {
        try {
            final var results = m.sendTypingMessage(stop ? TypingAction.STOP : TypingAction.START,
                    getSingleRecipientIdentifiers(List.of(recipient), m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public void sendReadReceipt(
            final String recipient,
            final List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity {
        final var results = m.sendReadReceipt(getSingleRecipientIdentifier(recipient, m.getSelfNumber()), messageIds);
        checkSendMessageResults(results);
    }

    @Override
    public void sendViewedReceipt(
            final String recipient,
            final List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity {
        final var results = m.sendViewedReceipt(getSingleRecipientIdentifier(recipient, m.getSelfNumber()), messageIds);
        checkSendMessageResults(results);
    }

    @Override
    public void sendContacts() {
        try {
            m.sendContacts();
        } catch (IOException e) {
            throw new Error.Failure("SendContacts error: " + e.getMessage());
        }
    }

    @Override
    public void sendSyncRequest() {
        try {
            m.requestAllSyncData();
        } catch (IOException e) {
            throw new Error.Failure("Request sync data error: " + e.getMessage());
        }
    }

    @Override
    public long sendNoteToSelfMessage(
            final String messageText,
            final List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure, Error.UntrustedIdentity {
        try {
            final var message = new Message(messageText,
                    attachments,
                    false,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    false);
            final var results = m.sendMessage(message, Set.of(RecipientIdentifier.NoteToSelf.INSTANCE), false);
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException | InvalidStickerException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public void sendEndSessionMessage(final List<String> recipients) {
        try {
            final var results = m.sendEndSessionMessage(getSingleRecipientIdentifiers(recipients, m.getSelfNumber()));
            checkSendMessageResults(results);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void deleteRecipient(final String recipient) throws Error.Failure {
        m.deleteRecipient(getSingleRecipientIdentifier(recipient, m.getSelfNumber()));
    }

    @Override
    public void deleteContact(final String recipient) throws Error.Failure {
        m.deleteContact(getSingleRecipientIdentifier(recipient, m.getSelfNumber()));
    }

    @Override
    public long sendGroupMessage(final String messageText, final List<String> attachments, final byte[] groupId) {
        try {
            final var message = new Message(messageText,
                    attachments,
                    false,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    List.of(),
                    false);
            var results = m.sendMessage(message, Set.of(getGroupRecipientIdentifier(groupId)), false);
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException | InvalidStickerException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public void sendGroupTyping(
            final byte[] groupId,
            final boolean stop
    ) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity {
        try {
            final var results = m.sendTypingMessage(stop ? TypingAction.STOP : TypingAction.START,
                    Set.of(getGroupRecipientIdentifier(groupId)));
            checkSendMessageResults(results);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendGroupRemoteDeleteMessage(final long targetSentTimestamp, final byte[] groupId) {
        try {
            final var results = m.sendRemoteDeleteMessage(targetSentTimestamp,
                    Set.of(getGroupRecipientIdentifier(groupId)));
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessageReaction(
            final String emoji,
            final boolean remove,
            final String targetAuthor,
            final long targetSentTimestamp,
            final byte[] groupId
    ) {
        try {
            final var results = m.sendMessageReaction(emoji,
                    remove,
                    getSingleRecipientIdentifier(targetAuthor, m.getSelfNumber()),
                    targetSentTimestamp,
                    Set.of(getGroupRecipientIdentifier(groupId)),
                    false,
                    false);
            checkSendMessageResults(results);
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    // Since contact names might be empty if not defined, also potentially return
    // the profile name
    @Override
    public String getContactName(final String number) {
        final var name = m.getContactOrProfileName(getSingleRecipientIdentifier(number, m.getSelfNumber()));
        return name == null ? "" : name;
    }

    @Override
    public void setContactName(final String number, final String name) {
        try {
            m.setContactName(getSingleRecipientIdentifier(number, m.getSelfNumber()), name, "", null, null, null);
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public void setExpirationTimer(final String number, final int expiration) {
        try {
            m.setExpirationTimer(getSingleRecipientIdentifier(number, m.getSelfNumber()), expiration);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public void setContactBlocked(final String number, final boolean blocked) {
        try {
            m.setContactsBlocked(List.of(getSingleRecipientIdentifier(number, m.getSelfNumber())), blocked);
        } catch (NotPrimaryDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    @Deprecated
    public void setGroupBlocked(final byte[] groupId, final boolean blocked) {
        try {
            m.setGroupsBlocked(List.of(getGroupId(groupId)), blocked);
        } catch (NotPrimaryDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (GroupNotFoundException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    @Deprecated
    public List<byte[]> getGroupIds() {
        var groups = m.getGroups();
        return groups.stream().map(g -> g.groupId().serialize()).toList();
    }

    @Override
    public DBusPath getGroup(final byte[] groupId) {
        updateGroups();
        final var groupOptional = groups.stream().filter(g -> Arrays.equals(g.getId(), groupId)).findFirst();
        if (groupOptional.isEmpty()) {
            throw new Error.GroupNotFound("Group not found");
        }
        return groupOptional.get().getObjectPath();
    }

    @Override
    public List<StructGroup> listGroups() {
        updateGroups();
        return groups;
    }

    @Override
    @Deprecated
    public String getGroupName(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null || group.title() == null) {
            return "";
        } else {
            return group.title();
        }
    }

    @Override
    @Deprecated
    public List<String> getGroupMembers(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return List.of();
        } else {
            final var members = group.members();
            return getRecipientStrings(members);
        }
    }

    @Override
    public byte[] createGroup(
            final String name,
            final List<String> members,
            final String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber {
        return updateGroupInternal(new byte[0], name, members, avatar);
    }

    @Override
    @Deprecated
    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) {
        return updateGroupInternal(groupId, name, members, avatar);
    }

    public byte[] updateGroupInternal(byte[] groupId, String name, List<String> members, String avatar) {
        try {
            groupId = nullIfEmpty(groupId);
            name = nullIfEmpty(name);
            avatar = nullIfEmpty(avatar);
            final var memberIdentifiers = getSingleRecipientIdentifiers(members, m.getSelfNumber());
            if (groupId == null) {
                final var results = m.createGroup(name, memberIdentifiers, avatar);
                updateGroups();
                checkGroupSendMessageResults(results.second().timestamp(), results.second().results());
                return results.first().serialize();
            } else {
                final var results = m.updateGroup(getGroupId(groupId),
                        UpdateGroup.newBuilder()
                                .withName(name)
                                .withMembers(memberIdentifiers)
                                .withAvatarFile(avatar)
                                .build());
                if (results != null) {
                    checkGroupSendMessageResults(results.timestamp(), results.results());
                }
                return groupId;
            }
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public boolean isRegistered(String number) {
        var result = isRegistered(List.of(number));
        return result.getFirst();
    }

    @Override
    public List<Boolean> isRegistered(List<String> numbers) {
        if (numbers.isEmpty()) {
            return List.of();
        }

        Map<String, UserStatus> registered;
        try {
            registered = m.getUserStatus(new HashSet<>(numbers));
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (RateLimitException e) {
            throw new Error.Failure(e.getMessage()
                    + ", retry at "
                    + DateUtils.formatTimestamp(e.getNextAttemptTimestamp()));
        }

        return numbers.stream().map(number -> registered.get(number).uuid() != null).toList();
    }

    @Override
    public void updateProfile(
            String givenName,
            String familyName,
            String about,
            String aboutEmoji,
            String avatarPath,
            final boolean removeAvatar
    ) {
        try {
            givenName = nullIfEmpty(givenName);
            familyName = nullIfEmpty(familyName);
            about = nullIfEmpty(about);
            aboutEmoji = nullIfEmpty(aboutEmoji);
            avatarPath = nullIfEmpty(avatarPath);
            final var avatarFile = removeAvatar || avatarPath == null ? null : avatarPath;
            m.updateProfile(UpdateProfile.newBuilder()
                    .withGivenName(givenName)
                    .withFamilyName(familyName)
                    .withAbout(about)
                    .withAboutEmoji(aboutEmoji)
                    .withAvatar(avatarFile)
                    .withDeleteAvatar(removeAvatar)
                    .build());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void updateProfile(
            final String name,
            final String about,
            final String aboutEmoji,
            String avatarPath,
            final boolean removeAvatar
    ) {
        updateProfile(name, "", about, aboutEmoji, avatarPath, removeAvatar);
    }

    @Override
    public void removePin() {
        try {
            m.setRegistrationLockPin(Optional.empty());
        } catch (IOException e) {
            throw new Error.Failure("Remove pin error: " + e.getMessage());
        } catch (NotPrimaryDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        }
    }

    @Override
    public void setPin(String registrationLockPin) {
        try {
            m.setRegistrationLockPin(Optional.of(registrationLockPin));
        } catch (IOException e) {
            throw new Error.Failure("Set pin error: " + e.getMessage());
        } catch (NotPrimaryDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        }
    }

    // Provide option to query a version string in order to react on potential
    // future interface changes
    @Override
    public String version() {
        return BaseConfig.PROJECT_VERSION;
    }

    // Create a unique list of Numbers from Identities and Contacts to really get
    // all numbers the system knows
    @Override
    public List<String> listNumbers() {
        return m.getRecipients(false, Optional.empty(), Set.of(), Optional.empty())
                .stream()
                .map(r -> r.getAddress().number().orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<String> getContactNumber(final String name) {
        return m.getRecipients(false, Optional.empty(), Set.of(), Optional.of(name))
                .stream()
                .map(r -> r.getAddress().getLegacyIdentifier())
                .toList();
    }

    @Override
    @Deprecated
    public void quitGroup(final byte[] groupId) {
        var group = getGroupId(groupId);
        try {
            m.quitGroup(group, Set.of());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException | LastGroupAdminException e) {
            throw new Error.Failure(e.getMessage());
        } catch (UnregisteredRecipientException e) {
            throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
        }
    }

    @Override
    public byte[] joinGroup(final String groupLink) {
        try {
            final var linkUrl = GroupInviteLinkUrl.fromUri(groupLink);
            if (linkUrl == null) {
                throw new Error.Failure("Group link is invalid:");
            }
            final var result = m.joinGroup(linkUrl);
            return result.first().serialize();
        } catch (PendingAdminApprovalException e) {
            throw new Error.Failure("Pending admin approval: " + e.getMessage());
        } catch (GroupInviteLinkUrl.InvalidGroupLinkException | InactiveGroupLinkException e) {
            throw new Error.Failure("Group link is invalid: " + e.getMessage());
        } catch (GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
            throw new Error.Failure("Group link was created with an incompatible version: " + e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public boolean isContactBlocked(final String number) {
        return m.isContactBlocked(getSingleRecipientIdentifier(number, m.getSelfNumber()));
    }

    @Override
    @Deprecated
    public boolean isGroupBlocked(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return false;
        } else {
            return group.isBlocked();
        }
    }

    @Override
    @Deprecated
    public boolean isMember(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return false;
        } else {
            return group.isMember();
        }
    }

    @Override
    public String uploadStickerPack(String stickerPackPath) {
        File path = new File(stickerPackPath);
        try {
            return m.uploadStickerPack(path).toString();
        } catch (IOException e) {
            throw new Error.Failure("Upload error (maybe image size is too large):" + e.getMessage());
        } catch (StickerPackInvalidException e) {
            throw new Error.Failure("Invalid sticker pack: " + e.getMessage());
        }
    }

    private static void checkSendMessageResult(long timestamp, SendMessageResult result) throws DBusExecutionException {
        var error = SendMessageResultUtils.getErrorMessageFromSendMessageResult(result);

        if (error == null) {
            return;
        }

        final var message = "\nFailed to send message:\n" + error + '\n' + timestamp;

        if (result.isIdentityFailure()) {
            throw new Error.UntrustedIdentity(message);
        } else {
            throw new Error.Failure(message);
        }
    }

    private void checkSendMessageResults(final SendMessageResults results) {
        final var sendMessageResults = results.results().values().stream().findFirst();
        if (results.results().size() == 1 && sendMessageResults.get().size() == 1) {
            checkSendMessageResult(results.timestamp(), sendMessageResults.get().stream().findFirst().get());
            return;
        }

        if (results.hasSuccess()) {
            return;
        }

        var message = new StringBuilder();
        message.append("Failed to send messages:\n");
        var errors = SendMessageResultUtils.getErrorMessagesFromSendMessageResults(results.results());
        for (var error : errors) {
            message.append(error).append('\n');
        }
        message.append(results.timestamp());

        throw new Error.Failure(message.toString());
    }

    private static void checkGroupSendMessageResults(
            long timestamp,
            Collection<SendMessageResult> results
    ) throws DBusExecutionException {
        if (results.size() == 1) {
            checkSendMessageResult(timestamp, results.stream().findFirst().get());
            return;
        }

        var errors = SendMessageResultUtils.getErrorMessagesFromSendMessageResults(results);
        if (errors.isEmpty() || errors.size() < results.size()) {
            return;
        }

        var message = new StringBuilder();
        message.append("Failed to send message:\n");
        for (var error : errors) {
            message.append(error).append('\n');
        }
        message.append(timestamp);

        throw new Error.Failure(message.toString());
    }

    private static List<String> getRecipientStrings(final Set<RecipientAddress> members) {
        return members.stream().map(RecipientAddress::getLegacyIdentifier).toList();
    }

    private static Set<RecipientIdentifier.Single> getSingleRecipientIdentifiers(
            final Collection<String> recipientStrings,
            final String localNumber
    ) throws DBusExecutionException {
        final var identifiers = new HashSet<RecipientIdentifier.Single>();
        for (var recipientString : recipientStrings) {
            identifiers.add(getSingleRecipientIdentifier(recipientString, localNumber));
        }
        return identifiers;
    }

    private static RecipientIdentifier.Single getSingleRecipientIdentifier(
            final String recipientString,
            final String localNumber
    ) throws DBusExecutionException {
        try {
            return RecipientIdentifier.Single.fromString(recipientString, localNumber);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    private RecipientIdentifier.Group getGroupRecipientIdentifier(final byte[] groupId) {
        return new RecipientIdentifier.Group(getGroupId(groupId));
    }

    private static GroupId getGroupId(byte[] groupId) throws DBusExecutionException {
        try {
            return GroupId.unknownVersion(groupId);
        } catch (Throwable e) {
            throw new Error.InvalidGroupId("Invalid group id: " + e.getMessage());
        }
    }

    private byte[] nullIfEmpty(final byte[] array) {
        return array.length == 0 ? null : array;
    }

    private String nullIfEmpty(final String name) {
        return name.isEmpty() ? null : name;
    }

    private String emptyIfNull(final String string) {
        return string == null ? "" : string;
    }

    private static String getDeviceObjectPath(String basePath, long deviceId) {
        return basePath + "/Devices/" + deviceId;
    }

    private void updateDevices() {
        List<org.asamk.signal.manager.api.Device> linkedDevices;
        try {
            linkedDevices = m.getLinkedDevices();
        } catch (IOException e) {
            throw new Error.Failure("Failed to get linked devices: " + e.getMessage());
        }

        unExportDevices();

        linkedDevices.forEach(d -> {
            final var object = new DbusSignalDeviceImpl(d);
            final var deviceObjectPath = object.getObjectPath();
            exportObject(object);
            if (d.isThisDevice()) {
                thisDevice = new DBusPath(deviceObjectPath);
            }
            this.devices.add(new StructDevice(new DBusPath(deviceObjectPath), (long) d.id(), emptyIfNull(d.name())));
        });
    }

    private void unExportDevices() {
        this.devices.stream()
                .map(StructDevice::getObjectPath)
                .map(DBusPath::getPath)
                .forEach(connection::unExportObject);
        this.devices.clear();
    }

    private static String getGroupObjectPath(String basePath, byte[] groupId) {
        return basePath + "/Groups/" + makeValidObjectPathElement(Base64.getEncoder().encodeToString(groupId));
    }

    private void updateGroups() {
        List<org.asamk.signal.manager.api.Group> groups;
        groups = m.getGroups();

        unExportGroups();

        groups.forEach(g -> {
            final var object = new DbusSignalGroupImpl(g.groupId());
            exportObject(object);
            this.groups.add(new StructGroup(new DBusPath(object.getObjectPath()),
                    g.groupId().serialize(),
                    emptyIfNull(g.title())));
        });
    }

    private void unExportGroups() {
        this.groups.stream().map(StructGroup::getObjectPath).map(DBusPath::getPath).forEach(connection::unExportObject);
        this.groups.clear();
    }

    private static String getConfigurationObjectPath(String basePath) {
        return basePath + "/Configuration";
    }

    private void updateConfiguration() {
        unExportConfiguration();
        final var object = new DbusSignalConfigurationImpl();
        exportObject(object);
    }

    private void unExportConfiguration() {
        final var objectPath = getConfigurationObjectPath(this.objectPath);
        connection.unExportObject(objectPath);
    }

    private void exportObject(final DBusInterface object) {
        try {
            connection.exportObject(object);
            logger.debug("Exported dbus object: " + object.getObjectPath());
        } catch (DBusException e) {
            logger.warn("Failed to export dbus object (" + object.getObjectPath() + "): " + e.getMessage());
        }
    }

    private void updateIdentities() {
        List<org.asamk.signal.manager.api.Identity> identities;
        identities = m.getIdentities();

        unExportIdentities();

        identities.forEach(i -> {
            final var object = new DbusSignalIdentityImpl(i);
            exportObject(object);
            this.identities.add(new StructIdentity(new DBusPath(object.getObjectPath()),
                    i.recipient().uuid().map(UUID::toString).orElse(""),
                    i.recipient().number().orElse("")));
        });
    }

    private static String getIdentityObjectPath(String basePath, String id) {
        return basePath + "/Identities/" + makeValidObjectPathElement(id);
    }

    private void unExportIdentities() {
        this.identities.stream()
                .map(StructIdentity::getObjectPath)
                .map(DBusPath::getPath)
                .forEach(connection::unExportObject);
        this.identities.clear();
    }

    @Override
    public DBusPath getIdentity(String number) throws Error.Failure {
        final var found = identities.stream()
                .filter(identity -> identity.getNumber().equals(number) || identity.getUuid().equals(number))
                .findFirst();

        if (found.isEmpty()) {
            throw new Error.Failure("Identity for " + number + " unknown");
        }
        return found.get().getObjectPath();
    }

    @Override
    public List<StructIdentity> listIdentities() {
        updateIdentities();
        return this.identities;
    }

    public class DbusSignalIdentityImpl extends DbusProperties implements Signal.Identity {

        private final org.asamk.signal.manager.api.Identity identity;

        public DbusSignalIdentityImpl(final org.asamk.signal.manager.api.Identity identity) {
            this.identity = identity;
            super.addPropertiesHandler(new DbusInterfacePropertiesHandler("org.asamk.Signal.Identity",
                    List.of(new DbusProperty<>("Number", () -> identity.recipient().number().orElse("")),
                            new DbusProperty<>("Uuid",
                                    () -> identity.recipient().uuid().map(UUID::toString).orElse("")),
                            new DbusProperty<>("Fingerprint", identity::fingerprint),
                            new DbusProperty<>("SafetyNumber", identity::safetyNumber),
                            new DbusProperty<>("ScannableSafetyNumber", identity::scannableSafetyNumber),
                            new DbusProperty<>("TrustLevel", identity::trustLevel),
                            new DbusProperty<>("AddedDate", identity::dateAddedTimestamp))));
        }

        @Override
        public String getObjectPath() {
            return getIdentityObjectPath(objectPath,
                    identity.recipient().getLegacyIdentifier() + "_" + identity.recipient().getIdentifier());
        }

        @Override
        public void trust() throws Error.Failure {
            var recipient = RecipientIdentifier.Single.fromAddress(identity.recipient());
            try {
                m.trustIdentityAllKeys(recipient);
            } catch (UnregisteredRecipientException e) {
                throw new Error.Failure("The user " + e.getSender().getIdentifier() + " is not registered.");
            }
            updateIdentities();
        }

        @Override
        public void trustVerified(String safetyNumber) throws Error.Failure {
            var recipient = RecipientIdentifier.Single.fromAddress(identity.recipient());

            if (safetyNumber == null) {
                throw new Error.Failure("You need to specify a fingerprint/safety number");
            }
            final IdentityVerificationCode verificationCode;
            try {
                verificationCode = IdentityVerificationCode.parse(safetyNumber);
            } catch (Exception e) {
                throw new Error.Failure(
                        "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
            }

            try {
                final var res = m.trustIdentityVerified(recipient, verificationCode);
                if (!res) {
                    throw new Error.Failure(
                            "Failed to set the trust for this number, make sure the number and the fingerprint/safety number are correct.");
                }
            } catch (UnregisteredRecipientException e) {
                throw new Error.Failure("The user " + e.getSender().getIdentifier() + " is not registered.");
            }
            updateIdentities();
        }
    }

    public class DbusSignalDeviceImpl extends DbusProperties implements Signal.Device {

        private final org.asamk.signal.manager.api.Device device;

        public DbusSignalDeviceImpl(final org.asamk.signal.manager.api.Device device) {
            super.addPropertiesHandler(new DbusInterfacePropertiesHandler("org.asamk.Signal.Device",
                    List.of(new DbusProperty<>("Id", device::id),
                            new DbusProperty<>("Name", () -> emptyIfNull(device.name()), this::setDeviceName),
                            new DbusProperty<>("Created", device::created),
                            new DbusProperty<>("LastSeen", device::lastSeen))));
            this.device = device;
        }

        @Override
        public String getObjectPath() {
            return getDeviceObjectPath(objectPath, device.id());
        }

        @Override
        public void removeDevice() throws Error.Failure {
            try {
                m.removeLinkedDevices(device.id());
                updateDevices();
            } catch (NotPrimaryDeviceException e) {
                throw new Error.Failure("This command doesn't work on linked devices.");
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
        }

        private void setDeviceName(String name) {
            if (!device.isThisDevice()) {
                throw new Error.Failure("Only the name of this device can be changed");
            }
            try {
                m.updateAccountAttributes(name, null, null, null);
                // update device list
                updateDevices();
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
        }
    }

    public class DbusSignalConfigurationImpl extends DbusProperties implements Signal.Configuration {

        public DbusSignalConfigurationImpl() {
            super.addPropertiesHandler(new DbusInterfacePropertiesHandler("org.asamk.Signal.Configuration",
                    List.of(new DbusProperty<>("ReadReceipts", this::getReadReceipts, this::setReadReceipts),
                            new DbusProperty<>("UnidentifiedDeliveryIndicators",
                                    this::getUnidentifiedDeliveryIndicators,
                                    this::setUnidentifiedDeliveryIndicators),
                            new DbusProperty<>("TypingIndicators",
                                    this::getTypingIndicators,
                                    this::setTypingIndicators),
                            new DbusProperty<>("LinkPreviews", this::getLinkPreviews, this::setLinkPreviews))));

        }

        @Override
        public String getObjectPath() {
            return getConfigurationObjectPath(objectPath);
        }

        public void setReadReceipts(Boolean readReceipts) {
            setConfiguration(readReceipts, null, null, null);
        }

        public void setUnidentifiedDeliveryIndicators(Boolean unidentifiedDeliveryIndicators) {
            setConfiguration(null, unidentifiedDeliveryIndicators, null, null);
        }

        public void setTypingIndicators(Boolean typingIndicators) {
            setConfiguration(null, null, typingIndicators, null);
        }

        public void setLinkPreviews(Boolean linkPreviews) {
            setConfiguration(null, null, null, linkPreviews);
        }

        private void setConfiguration(
                Boolean readReceipts,
                Boolean unidentifiedDeliveryIndicators,
                Boolean typingIndicators,
                Boolean linkPreviews
        ) {
            try {
                m.updateConfiguration(new org.asamk.signal.manager.api.Configuration(Optional.ofNullable(readReceipts),
                        Optional.ofNullable(unidentifiedDeliveryIndicators),
                        Optional.ofNullable(typingIndicators),
                        Optional.ofNullable(linkPreviews)));
            } catch (NotPrimaryDeviceException e) {
                throw new Error.Failure("This command doesn't work on linked devices.");
            }
        }

        private boolean getReadReceipts() {
            return m.getConfiguration().readReceipts().orElse(false);
        }

        private boolean getUnidentifiedDeliveryIndicators() {
            return m.getConfiguration().unidentifiedDeliveryIndicators().orElse(false);
        }

        private boolean getTypingIndicators() {
            return m.getConfiguration().typingIndicators().orElse(false);
        }

        private boolean getLinkPreviews() {
            return m.getConfiguration().linkPreviews().orElse(false);
        }
    }

    public class DbusSignalGroupImpl extends DbusProperties implements Signal.Group {

        private final GroupId groupId;

        public DbusSignalGroupImpl(final GroupId groupId) {
            this.groupId = groupId;
            super.addPropertiesHandler(new DbusInterfacePropertiesHandler("org.asamk.Signal.Group",
                    List.of(new DbusProperty<>("Id", groupId::serialize),
                            new DbusProperty<>("Name", () -> emptyIfNull(getGroup().title()), this::setGroupName),
                            new DbusProperty<>("Description",
                                    () -> emptyIfNull(getGroup().description()),
                                    this::setGroupDescription),
                            new DbusProperty<>("Avatar", this::setGroupAvatar),
                            new DbusProperty<>("IsBlocked", () -> getGroup().isBlocked(), this::setIsBlocked),
                            new DbusProperty<>("IsMember", () -> getGroup().isMember()),
                            new DbusProperty<>("IsAdmin", () -> getGroup().isAdmin()),
                            new DbusProperty<>("MessageExpirationTimer",
                                    () -> getGroup().messageExpirationTimer(),
                                    this::setMessageExpirationTime),
                            new DbusProperty<>("Members",
                                    () -> new Variant<>(getRecipientStrings(getGroup().members()), "as")),
                            new DbusProperty<>("PendingMembers",
                                    () -> new Variant<>(getRecipientStrings(getGroup().pendingMembers()), "as")),
                            new DbusProperty<>("RequestingMembers",
                                    () -> new Variant<>(getRecipientStrings(getGroup().requestingMembers()), "as")),
                            new DbusProperty<>("Admins",
                                    () -> new Variant<>(getRecipientStrings(getGroup().adminMembers()), "as")),
                            new DbusProperty<>("Banned",
                                    () -> new Variant<>(getRecipientStrings(getGroup().bannedMembers()), "as")),
                            new DbusProperty<>("PermissionAddMember",
                                    () -> getGroup().permissionAddMember().name(),
                                    this::setGroupPermissionAddMember),
                            new DbusProperty<>("PermissionEditDetails",
                                    () -> getGroup().permissionEditDetails().name(),
                                    this::setGroupPermissionEditDetails),
                            new DbusProperty<>("PermissionSendMessage",
                                    () -> getGroup().permissionSendMessage().name(),
                                    this::setGroupPermissionSendMessage),
                            new DbusProperty<>("GroupInviteLink", () -> {
                                final var groupInviteLinkUrl = getGroup().groupInviteLinkUrl();
                                return groupInviteLinkUrl == null ? "" : groupInviteLinkUrl.getUrl();
                            }))));
        }

        @Override
        public String getObjectPath() {
            return getGroupObjectPath(objectPath, groupId.serialize());
        }

        @Override
        public void quitGroup() throws Error.Failure {
            try {
                m.quitGroup(groupId, Set.of());
            } catch (GroupNotFoundException e) {
                throw new Error.GroupNotFound(e.getMessage());
            } catch (NotAGroupMemberException e) {
                throw new Error.NotAGroupMember(e.getMessage());
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            } catch (LastGroupAdminException e) {
                throw new Error.LastGroupAdmin(e.getMessage());
            } catch (UnregisteredRecipientException e) {
                throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
            }
        }

        @Override
        public void deleteGroup() throws Error.Failure, Error.LastGroupAdmin {
            try {
                m.deleteGroup(groupId);
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
            updateGroups();
        }

        @Override
        public void addMembers(final List<String> recipients) throws Error.Failure {
            final var memberIdentifiers = getSingleRecipientIdentifiers(recipients, m.getSelfNumber());
            updateGroup(UpdateGroup.newBuilder().withMembers(memberIdentifiers).build());
        }

        @Override
        public void removeMembers(final List<String> recipients) throws Error.Failure {
            final var memberIdentifiers = getSingleRecipientIdentifiers(recipients, m.getSelfNumber());
            updateGroup(UpdateGroup.newBuilder().withRemoveMembers(memberIdentifiers).build());
        }

        @Override
        public void addAdmins(final List<String> recipients) throws Error.Failure {
            final var memberIdentifiers = getSingleRecipientIdentifiers(recipients, m.getSelfNumber());
            updateGroup(UpdateGroup.newBuilder().withAdmins(memberIdentifiers).build());
        }

        @Override
        public void removeAdmins(final List<String> recipients) throws Error.Failure {
            final var memberIdentifiers = getSingleRecipientIdentifiers(recipients, m.getSelfNumber());
            updateGroup(UpdateGroup.newBuilder().withRemoveAdmins(memberIdentifiers).build());
        }

        @Override
        public void resetLink() throws Error.Failure {
            updateGroup(UpdateGroup.newBuilder().withResetGroupLink(true).build());
        }

        @Override
        public void disableLink() throws Error.Failure {
            updateGroup(UpdateGroup.newBuilder().withGroupLinkState(GroupLinkState.DISABLED).build());
        }

        @Override
        public void enableLink(final boolean requiresApproval) throws Error.Failure {
            updateGroup(UpdateGroup.newBuilder()
                    .withGroupLinkState(requiresApproval
                            ? GroupLinkState.ENABLED_WITH_APPROVAL
                            : GroupLinkState.ENABLED)
                    .build());
        }

        private org.asamk.signal.manager.api.Group getGroup() {
            return m.getGroup(groupId);
        }

        private void setGroupName(final String name) {
            updateGroup(UpdateGroup.newBuilder().withName(name).build());
        }

        private void setGroupDescription(final String description) {
            updateGroup(UpdateGroup.newBuilder().withDescription(description).build());
        }

        private void setGroupAvatar(final String avatar) {
            updateGroup(UpdateGroup.newBuilder().withAvatarFile(avatar).build());
        }

        private void setMessageExpirationTime(final int expirationTime) {
            updateGroup(UpdateGroup.newBuilder().withExpirationTimer(expirationTime).build());
        }

        private void setGroupPermissionAddMember(final String permission) {
            updateGroup(UpdateGroup.newBuilder().withAddMemberPermission(GroupPermission.valueOf(permission)).build());
        }

        private void setGroupPermissionEditDetails(final String permission) {
            updateGroup(UpdateGroup.newBuilder()
                    .withEditDetailsPermission(GroupPermission.valueOf(permission))
                    .build());
        }

        private void setGroupPermissionSendMessage(final String permission) {
            updateGroup(UpdateGroup.newBuilder()
                    .withIsAnnouncementGroup(GroupPermission.valueOf(permission) == GroupPermission.ONLY_ADMINS)
                    .build());
        }

        private void setIsBlocked(final boolean isBlocked) {
            try {
                m.setGroupsBlocked(List.of(groupId), isBlocked);
            } catch (NotPrimaryDeviceException e) {
                throw new Error.Failure("This command doesn't work on linked devices.");
            } catch (GroupNotFoundException e) {
                throw new Error.GroupNotFound(e.getMessage());
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
        }

        private void updateGroup(final UpdateGroup updateGroup) {
            try {
                m.updateGroup(groupId, updateGroup);
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
                throw new Error.GroupNotFound(e.getMessage());
            } catch (AttachmentInvalidException e) {
                throw new Error.AttachmentInvalid(e.getMessage());
            } catch (UnregisteredRecipientException e) {
                throw new Error.UntrustedIdentity(e.getSender().getIdentifier() + " is not registered.");
            }
        }
    }
}
