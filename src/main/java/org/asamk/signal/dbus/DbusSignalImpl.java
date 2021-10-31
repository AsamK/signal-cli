package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.api.UpdateGroup;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.util.ErrorUtils;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.types.Variant;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

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
import java.util.stream.Stream;

public class DbusSignalImpl implements Signal {

    private final Manager m;
    private final DBusConnection connection;
    private final String objectPath;

    private DBusPath thisDevice;
    private final List<StructDevice> devices = new ArrayList<>();
    private final List<StructGroup> groups = new ArrayList<>();

    public DbusSignalImpl(final Manager m, DBusConnection connection, final String objectPath) {
        this.m = m;
        this.connection = connection;
        this.objectPath = objectPath;
    }

    public void initObjects() {
        updateDevices();
        updateGroups();
    }

    public void close() {
        unExportDevices();
        unExportGroups();
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
    public void submitRateLimitChallenge(String challenge, String captchaString) throws IOErrorException {
        final var captcha = captchaString == null ? null : captchaString.replace("signalcaptcha://", "");

        try {
            m.submitRateLimitRecaptchaChallenge(challenge, captcha);
        } catch (IOException e) {
            throw new IOErrorException("Submit challenge error: " + e.getMessage(), e);
        }

    }

    @Override
    public void addDevice(String uri) {
        try {
            m.addDeviceLink(new URI(uri));
        } catch (IOException | InvalidKeyException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + " Add device link failed. " + e.getMessage());
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
        var recipients = new ArrayList<String>(1);
        recipients.add(recipient);
        return sendMessage(message, attachments, recipients);
    }

    @Override
    public long sendMessage(final String message, final List<String> attachments, final List<String> recipients) {
        try {
            final var results = m.sendMessage(new Message(message, attachments),
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));

            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e);
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendRemoteDeleteMessage(
            final long targetSentTimestamp, final String recipient
    ) {
        var recipients = new ArrayList<String>(1);
        recipients.add(recipient);
        return sendRemoteDeleteMessage(targetSentTimestamp, recipients);
    }

    @Override
    public long sendRemoteDeleteMessage(
            final long targetSentTimestamp, final List<String> recipients
    ) {
        try {
            final var results = m.sendRemoteDeleteMessage(targetSentTimestamp,
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendGroupRemoteDeleteMessage(
            final long targetSentTimestamp, final byte[] groupId
    ) {
        try {
            final var results = m.sendRemoteDeleteMessage(targetSentTimestamp,
                    Set.of(new RecipientIdentifier.Group(getGroupId(groupId))));
            checkSendMessageResults(results.timestamp(), results.results());
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
        var recipients = new ArrayList<String>(1);
        recipients.add(recipient);
        return sendMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, recipients);
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
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public void sendTyping(
            final String recipient, final boolean stop
    ) throws Error.Failure, Error.GroupNotFound, Error.UntrustedIdentity {
        try {
            var recipients = new ArrayList<String>(1);
            recipients.add(recipient);
            m.sendTypingMessage(stop ? TypingAction.STOP : TypingAction.START,
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (UntrustedIdentityException e) {
            throw new Error.UntrustedIdentity(e.getMessage());
        }
    }

    @Override
    public void sendReadReceipt(
            final String recipient, final List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity {
        try {
            m.sendReadReceipt(getSingleRecipientIdentifier(recipient, m.getSelfNumber()), messageIds);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (UntrustedIdentityException e) {
            throw new Error.UntrustedIdentity(e.getMessage());
        }
    }

    @Override
    public void sendViewedReceipt(
            final String recipient, final List<Long> messageIds
    ) throws Error.Failure, Error.UntrustedIdentity {
        try {
            m.sendViewedReceipt(getSingleRecipientIdentifier(recipient, m.getSelfNumber()), messageIds);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (UntrustedIdentityException e) {
            throw new Error.UntrustedIdentity(e.getMessage());
        }
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
            final String message, final List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure, Error.UntrustedIdentity {
        try {
            final var results = m.sendMessage(new Message(message, attachments),
                    Set.of(RecipientIdentifier.NoteToSelf.INSTANCE));
            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public void sendEndSessionMessage(final List<String> recipients) {
        try {
            final var results = m.sendEndSessionMessage(getSingleRecipientIdentifiers(recipients, m.getSelfNumber()));
            checkSendMessageResults(results.timestamp(), results.results());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessage(final String message, final List<String> attachments, final byte[] groupId) {
        try {
            var results = m.sendMessage(new Message(message, attachments),
                    Set.of(new RecipientIdentifier.Group(getGroupId(groupId))));
            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
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
                    Set.of(new RecipientIdentifier.Group(getGroupId(groupId))));
            checkSendMessageResults(results.timestamp(), results.results());
            return results.timestamp();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
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
            m.setContactName(getSingleRecipientIdentifier(number, m.getSelfNumber()), name);
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (UnregisteredUserException e) {
            throw new Error.Failure("Contact is not registered.");
        }
    }

    @Override
    public void setExpirationTimer(final String number, final int expiration) {
        try {
            m.setExpirationTimer(getSingleRecipientIdentifier(number, m.getSelfNumber()), expiration);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void setContactBlocked(final String number, final boolean blocked) {
        try {
            m.setContactBlocked(getSingleRecipientIdentifier(number, m.getSelfNumber()), blocked);
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void setGroupBlocked(final byte[] groupId, final boolean blocked) {
        try {
            m.setGroupBlocked(getGroupId(groupId), blocked);
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (GroupNotFoundException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public List<byte[]> getGroupIds() {
        var groups = m.getGroups();
        var ids = new ArrayList<byte[]>(groups.size());
        for (var group : groups) {
            ids.add(group.groupId().serialize());
        }
        return ids;
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
    public String getGroupName(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null || group.title() == null) {
            return "";
        } else {
            return group.title();
        }
    }

    @Override
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
            final String name, final List<String> members, final String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber {
        return updateGroup(new byte[0], name, members, avatar);
    }

    @Override
    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) {
        try {
            groupId = nullIfEmpty(groupId);
            name = nullIfEmpty(name);
            avatar = nullIfEmpty(avatar);
            final var memberIdentifiers = getSingleRecipientIdentifiers(members, m.getSelfNumber());
            if (groupId == null) {
                final var results = m.createGroup(name, memberIdentifiers, avatar == null ? null : new File(avatar));
                checkSendMessageResults(results.second().timestamp(), results.second().results());
                return results.first().serialize();
            } else {
                final var results = m.updateGroup(getGroupId(groupId),
                        UpdateGroup.newBuilder()
                                .withName(name)
                                .withMembers(memberIdentifiers)
                                .withAvatarFile(avatar == null ? null : new File(avatar))
                                .build());
                if (results != null) {
                    checkSendMessageResults(results.timestamp(), results.results());
                }
                return groupId;
            }
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        }
    }

    @Override
    public boolean isRegistered() {
        return true;
    }

    @Override
    public boolean isRegistered(String number) {
        var result = isRegistered(List.of(number));
        return result.get(0);
    }

    @Override
    public List<Boolean> isRegistered(List<String> numbers) {
        var results = new ArrayList<Boolean>();
        if (numbers.isEmpty()) {
            return results;
        }

        Map<String, Pair<String, UUID>> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(numbers));
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }

        return numbers.stream().map(number -> {
            var uuid = registered.get(number).second();
            return uuid != null;
        }).collect(Collectors.toList());
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
            Optional<File> avatarFile = removeAvatar
                    ? Optional.empty()
                    : avatarPath == null ? null : Optional.of(new File(avatarPath));
            m.setProfile(givenName, familyName, about, aboutEmoji, avatarFile);
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
        } catch (UnauthenticatedResponseException e) {
            throw new Error.Failure("Remove pin failed with unauthenticated response: " + e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure("Remove pin error: " + e.getMessage());
        }
    }

    @Override
    public void setPin(String registrationLockPin) {
        try {
            m.setRegistrationLockPin(Optional.of(registrationLockPin));
        } catch (UnauthenticatedResponseException e) {
            throw new Error.Failure("Set pin error failed with unauthenticated response: " + e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure("Set pin error: " + e.getMessage());
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
        return Stream.concat(m.getIdentities().stream().map(Identity::recipient),
                        m.getContacts().stream().map(Pair::first))
                .map(a -> a.getNumber().orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getContactNumber(final String name) {
        // Contact names have precedence.
        var numbers = new ArrayList<String>();
        var contacts = m.getContacts();
        for (var c : contacts) {
            if (name.equals(c.second().getName())) {
                numbers.add(c.first().getLegacyIdentifier());
            }
        }
        // Try profiles if no contact name was found
        for (var identity : m.getIdentities()) {
            final var address = identity.recipient();
            var number = address.getNumber().orElse(null);
            if (number != null) {
                Profile profile = null;
                try {
                    profile = m.getRecipientProfile(RecipientIdentifier.Single.fromAddress(address));
                } catch (UnregisteredUserException ignored) {
                }
                if (profile != null && profile.getDisplayName().equals(name)) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    @Override
    public void quitGroup(final byte[] groupId) {
        var group = getGroupId(groupId);
        try {
            m.quitGroup(group, Set.of());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException | LastGroupAdminException e) {
            throw new Error.Failure(e.getMessage());
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
        } catch (GroupInviteLinkUrl.InvalidGroupLinkException | GroupLinkNotActiveException e) {
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
    public boolean isGroupBlocked(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return false;
        } else {
            return group.isBlocked();
        }
    }

    @Override
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
        var error = ErrorUtils.getErrorMessageFromSendMessageResult(result);

        if (error == null) {
            return;
        }

        final var message = timestamp + "\nFailed to send message:\n" + error + '\n';

        if (result.getIdentityFailure() != null) {
            throw new Error.UntrustedIdentity(message);
        } else {
            throw new Error.Failure(message);
        }
    }

    private static void checkSendMessageResults(
            long timestamp, Map<RecipientIdentifier, List<SendMessageResult>> results
    ) throws DBusExecutionException {
        final var sendMessageResults = results.values().stream().findFirst();
        if (results.size() == 1 && sendMessageResults.get().size() == 1) {
            checkSendMessageResult(timestamp, sendMessageResults.get().stream().findFirst().get());
            return;
        }

        var errors = ErrorUtils.getErrorMessagesFromSendMessageResults(results);
        if (errors.size() == 0) {
            return;
        }

        var message = new StringBuilder();
        message.append(timestamp).append('\n');
        message.append("Failed to send (some) messages:\n");
        for (var error : errors) {
            message.append(error).append('\n');
        }

        throw new Error.Failure(message.toString());
    }

    private static void checkSendMessageResults(
            long timestamp, Collection<SendMessageResult> results
    ) throws DBusExecutionException {
        if (results.size() == 1) {
            checkSendMessageResult(timestamp, results.stream().findFirst().get());
            return;
        }

        var errors = ErrorUtils.getErrorMessagesFromSendMessageResults(results);
        if (errors.size() == 0) {
            return;
        }

        var message = new StringBuilder();
        message.append(timestamp).append('\n');
        message.append("Failed to send (some) messages:\n");
        for (var error : errors) {
            message.append(error).append('\n');
        }

        throw new Error.Failure(message.toString());
    }

    private static List<String> getRecipientStrings(final Set<RecipientAddress> members) {
        return members.stream().map(RecipientAddress::getLegacyIdentifier).collect(Collectors.toList());
    }

    private static Set<RecipientIdentifier.Single> getSingleRecipientIdentifiers(
            final Collection<String> recipientStrings, final String localNumber
    ) throws DBusExecutionException {
        final var identifiers = new HashSet<RecipientIdentifier.Single>();
        for (var recipientString : recipientStrings) {
            identifiers.add(getSingleRecipientIdentifier(recipientString, localNumber));
        }
        return identifiers;
    }

    private static RecipientIdentifier.Single getSingleRecipientIdentifier(
            final String recipientString, final String localNumber
    ) throws DBusExecutionException {
        try {
            return RecipientIdentifier.Single.fromString(recipientString, localNumber);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
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
            try {
                connection.exportObject(object);
            } catch (DBusException e) {
                e.printStackTrace();
            }
            if (d.isThisDevice()) {
                thisDevice = new DBusPath(deviceObjectPath);
            }
            this.devices.add(new StructDevice(new DBusPath(deviceObjectPath), d.id(), emptyIfNull(d.name())));
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
        return basePath + "/Groups/" + Base64.getEncoder()
                .encodeToString(groupId)
                .replace("+", "_")
                .replace("/", "_")
                .replace("=", "_");
    }

    private void updateGroups() {
        List<org.asamk.signal.manager.api.Group> groups;
        groups = m.getGroups();

        unExportGroups();

        groups.forEach(g -> {
            final var object = new DbusSignalGroupImpl(g.groupId());
            try {
                connection.exportObject(object);
            } catch (DBusException e) {
                e.printStackTrace();
            }
            this.groups.add(new StructGroup(new DBusPath(object.getObjectPath()),
                    g.groupId().serialize(),
                    emptyIfNull(g.title())));
        });
    }

    private void unExportGroups() {
        this.groups.stream().map(StructGroup::getObjectPath).map(DBusPath::getPath).forEach(connection::unExportObject);
        this.groups.clear();
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
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
        }

        private void setDeviceName(String name) {
            if (!device.isThisDevice()) {
                throw new Error.Failure("Only the name of this device can be changed");
            }
            try {
                m.updateAccountAttributes(name);
                // update device list
                updateDevices();
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
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
            } catch (GroupNotFoundException | NotAGroupMemberException e) {
                throw new Error.GroupNotFound(e.getMessage());
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            } catch (LastGroupAdminException e) {
                throw new Error.LastGroupAdmin(e.getMessage());
            }
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
            updateGroup(UpdateGroup.newBuilder().withAvatarFile(new File(avatar)).build());
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
                m.setGroupBlocked(groupId, isBlocked);
            } catch (NotMasterDeviceException e) {
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
            }
        }
    }
}
