package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class DbusSignalImpl implements Signal {

    private final Manager m;
    private final String objectPath;

    public DbusSignalImpl(final Manager m, final String objectPath) {
        this.m = m;
        this.objectPath = objectPath;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return objectPath;
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
    public void removeDevice(int deviceId) {
        try {
            m.removeLinkedDevices(deviceId);
        } catch (IOException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + ": Error while removing device: " + e.getMessage());
        }
    }

    @Override
    public List<String> listDevices() {
        List<Device> devices;
        List<String> results = new ArrayList<String>();

        try {
            devices = m.getLinkedDevices();
        } catch (IOException | Error.Failure e) {
            throw new Error.Failure("Failed to get linked devices: " + e.getMessage());
        }

        return devices.stream().map(d -> d.getName() == null ? "" : d.getName()).collect(Collectors.toList());
    }

    @Override
    public void updateDeviceName(String deviceName) {
        try {
            m.updateAccountAttributes(deviceName);
        } catch (IOException | Signal.Error.Failure e) {
            throw new Error.Failure("UpdateAccount error: " + e.getMessage());
        }
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
                    getSingleRecipientIdentifiers(recipients, m.getUsername()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));

            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
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
                    getSingleRecipientIdentifiers(recipients, m.getUsername()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
                    getSingleRecipientIdentifier(targetAuthor, m.getUsername()),
                    targetSentTimestamp,
                    getSingleRecipientIdentifiers(recipients, m.getUsername()).stream()
                            .map(RecipientIdentifier.class::cast)
                            .collect(Collectors.toSet()));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
                    getSingleRecipientIdentifiers(recipients, m.getUsername()).stream()
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
            final String recipient, final List<Long> timestamps
    ) throws Error.Failure, Error.UntrustedIdentity {
        try {
            m.sendReadReceipt(getSingleRecipientIdentifier(recipient, m.getUsername()), timestamps);
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
                    Set.of(new RecipientIdentifier.NoteToSelf()));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
            final var results = m.sendEndSessionMessage(getSingleRecipientIdentifiers(recipients, m.getUsername()));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessage(final String message, final List<String> attachments, final byte[] groupId) {
        try {
            var results = m.sendMessage(new Message(message, attachments),
                    Set.of(new RecipientIdentifier.Group(getGroupId(groupId))));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
                    getSingleRecipientIdentifier(targetAuthor, m.getUsername()),
                    targetSentTimestamp,
                    Set.of(new RecipientIdentifier.Group(getGroupId(groupId))));
            checkSendMessageResults(results.getTimestamp(), results.getResults());
            return results.getTimestamp();
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
        return m.getContactOrProfileName(getSingleRecipientIdentifier(number, m.getUsername()));
    }

    @Override
    public void setContactName(final String number, final String name) {
        try {
            m.setContactName(getSingleRecipientIdentifier(number, m.getUsername()), name);
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        } catch (UnregisteredUserException e) {
            throw new Error.Failure("Contact is not registered.");
        }
    }

    @Override
    public void setExpirationTimer(final String number, final int expiration) {
        try {
            m.setExpirationTimer(getSingleRecipientIdentifier(number, m.getUsername()), expiration);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void setContactBlocked(final String number, final boolean blocked) {
        try {
            m.setContactBlocked(getSingleRecipientIdentifier(number, m.getUsername()), blocked);
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
            ids.add(group.getGroupId().serialize());
        }
        return ids;
    }

    @Override
    public String getGroupName(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return "";
        } else {
            return group.getTitle();
        }
    }

    @Override
    public List<String> getGroupMembers(final byte[] groupId) {
        var group = m.getGroup(getGroupId(groupId));
        if (group == null) {
            return List.of();
        } else {
            return group.getMembers()
                    .stream()
                    .map(m::resolveSignalServiceAddress)
                    .map(Util::getLegacyIdentifier)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) {
        try {
            if (groupId.length == 0) {
                groupId = null;
            }
            if (name.isEmpty()) {
                name = null;
            }
            if (avatar.isEmpty()) {
                avatar = null;
            }
            final var memberIdentifiers = getSingleRecipientIdentifiers(members, m.getUsername());
            if (groupId == null) {
                final var results = m.createGroup(name, memberIdentifiers, avatar == null ? null : new File(avatar));
                checkSendMessageResults(results.second().getTimestamp(), results.second().getResults());
                return results.first().serialize();
            } else {
                final var results = m.updateGroup(getGroupId(groupId),
                        name,
                        null,
                        memberIdentifiers,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        avatar == null ? null : new File(avatar),
                        null,
                        null);
                if (results != null) {
                    checkSendMessageResults(results.getTimestamp(), results.getResults());
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
            final String name,
            final String about,
            final String aboutEmoji,
            String avatarPath,
            final boolean removeAvatar
    ) {
        try {
            if (avatarPath.isEmpty()) {
                avatarPath = null;
            }
            Optional<File> avatarFile = removeAvatar
                    ? Optional.absent()
                    : avatarPath == null ? null : Optional.of(new File(avatarPath));
            m.setProfile(name, null, about, aboutEmoji, avatarFile);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void removePin() {
        try {
            m.setRegistrationLockPin(Optional.absent());
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
        return Stream.concat(m.getIdentities().stream().map(IdentityInfo::getRecipientId),
                m.getContacts().stream().map(Pair::first))
                .map(m::resolveSignalServiceAddress)
                .map(a -> a.getNumber().orNull())
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
                numbers.add(getLegacyIdentifier(m.resolveSignalServiceAddress(c.first())));
            }
        }
        // Try profiles if no contact name was found
        for (var identity : m.getIdentities()) {
            final var recipientId = identity.getRecipientId();
            final var address = m.resolveSignalServiceAddress(recipientId);
            var number = address.getNumber().orNull();
            if (number != null) {
                var profile = m.getRecipientProfile(recipientId);
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
        return m.isContactBlocked(getSingleRecipientIdentifier(number, m.getUsername()));
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
            return group.isMember(m.getSelfRecipientId());
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
}
