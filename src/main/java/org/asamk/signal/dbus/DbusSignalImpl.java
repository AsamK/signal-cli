package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.Signal.Error;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.commands.UpdateGroupCommand;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.Group;
import org.asamk.signal.manager.api.Identity;
import org.asamk.signal.manager.api.Message;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupSendingNotAllowedException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbusSignalImpl implements Signal {

    private final Manager m;
    private final DBusConnection connection;
    private final String objectPath;

    private DBusPath thisDevice;
    private final List<DBusPath> devices = new ArrayList<>();

    public DbusSignalImpl(final Manager m, DBusConnection connection, final String objectPath) {
        this.m = m;
        this.connection = connection;
        this.objectPath = objectPath;
    }

    public void initObjects() {
        updateDevices();
    }

    public void close() {
        unExportDevices();
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
        if (deviceId < 0) {
            throw new Error.InvalidNumber("Invalid deviceId: Negative number");
        }
        updateDevices();
        return new DBusPath(getDeviceObjectPath(objectPath, deviceId));
    }

    @Override
    public List<DBusPath> listDevices() {
        updateDevices();
        return this.devices;
    }

    private void updateDevices() {
        List<org.asamk.signal.manager.api.Device> linkedDevices;
        try {
            linkedDevices = m.getLinkedDevices();
        } catch (IOException | Error.Failure e) {
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
            this.devices.add(new DBusPath(deviceObjectPath));
        });
    }

    private void unExportDevices() {
        this.devices.stream().map(DBusPath::getPath).forEach(connection::unExportObject);
        this.devices.clear();
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
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
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
                    getSingleRecipientIdentifier(targetAuthor, m.getSelfNumber()),
                    targetSentTimestamp,
                    getSingleRecipientIdentifiers(recipients, m.getSelfNumber()).stream()
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
            final var results = m.sendEndSessionMessage(getSingleRecipientIdentifiers(recipients, m.getSelfNumber()));
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
                    getSingleRecipientIdentifier(targetAuthor, m.getSelfNumber()),
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

    @Override
    public void sendTyping(boolean typingAction, List<String> groupIdStrings, List<String> numbers) {
        final boolean noNumbers = numbers == null || numbers.isEmpty();
        final boolean noGroup = groupIdStrings == null || groupIdStrings.isEmpty();
        if (noNumbers && noGroup) {
            throw new Error.Failure("No recipients given");
        }

        final TypingAction action = typingAction ? TypingAction.START : TypingAction.STOP;
        final var timestamp = System.currentTimeMillis();
        final var localNumber = m.getSelfNumber();
        Set<RecipientIdentifier> recipients = new HashSet<RecipientIdentifier>();

        try {
            if (!noGroup) {
                recipients.addAll(CommandUtil.getGroupIdentifiers(groupIdStrings));
            }

            if (!noNumbers) {
                recipients.addAll(CommandUtil.getSingleRecipientIdentifiers(numbers, localNumber));
            }
            m.sendTypingMessage(action, recipients);
        } catch (IOException e) {
            throw new Error.Failure("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.InvalidGroupId("Invalid group id: " + e.getMessage());
        } catch (UserErrorException e) {
            throw new Error.Failure("Invalid number: " + e.getMessage());
        } catch (Exception e) {
            throw new Error.UntrustedIdentity("Failed to send message: " + e.getMessage());
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
        GroupId group = null;
        try {
            group = getGroupId(groupId);
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        }
        try {
            m.setGroupBlocked(group, blocked);
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
    public byte[] getGroupId(String groupName) {
        List<Group> groups = m.getGroups();
        for (Group group : groups) {
            if (groupName.equals(group.getTitle())) {
                return group.getGroupId().serialize();
            }
        }
        throw new Error.GroupNotFound(groupName);
    }

    @Override
    public List<String> getGroupIdStrings() {
        List<Group> groups = m.getGroups();
        List<String> idStrings = new ArrayList<>(groups.size());
        for (Group group : groups) {
            idStrings.add(group.getGroupId().toBase64());
        }
        return idStrings;
    }

    @Override
    public String getGroupIdString(String groupName) {
        List<Group> groups = m.getGroups();
        for (Group group : groups) {
            if (groupName.equals(group.getTitle())) {
                return group.getGroupId().toBase64();
            }
        }
        throw new Error.GroupNotFound(groupName);
    }

    @Override
    public List<String> getGroupNames() {
        List<Group> groups = m.getGroups();
        List<String> groupNames = new ArrayList<>(groups.size());
        for (Group group : groups) {
            groupNames.add(group.getTitle());
        }
        return groupNames;
    }

    @Override
    public String getGroupName(final byte[] groupId) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            return group.getTitle();
        }
    }

    @Override
    public List<String> getGroupMembers(final byte[] groupId) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            return group.getMembers().stream().map(RecipientAddress::getLegacyIdentifier).collect(Collectors.toList());
        }
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
    public byte[] updateGroup(
            byte[] groupId,
            String name,
            String description,
            List<String> addMembers,
            List<String> removeMembers,
            List<String> addAdmins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            String groupLinkState,
            String addMemberPermission,
            String editDetailsPermission,
            String avatar,
            Integer expirationTimer
            ) {
        return updateGroup(
                groupId,
                name,
                description,
                addMembers,
                removeMembers,
                addAdmins,
                removeAdmins,
                resetGroupLink,
                groupLinkState,
                addMemberPermission,
                editDetailsPermission,
                avatar,
                expirationTimer,
                isGroupAnnounceOnly(groupId));
    }

    @Override
    public byte[] updateGroup(
            byte[] groupId,
            String name,
            String description,
            List<String> addMembers,
            List<String> removeMembers,
            List<String> addAdmins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            String groupLinkState,
            String addMemberPermission,
            String editDetailsPermission,
            String avatar,
            Integer expirationTimer,
            Boolean isAnnouncementGroup
            ) {
        try {
            File avatarFile = null;
            if (name.isEmpty()) {
                name = null;
            }
            if (description.isEmpty()) {
                description= null;
            }
            if (addMembers.isEmpty()) {
                addMembers = null;
            }
            if (removeMembers.isEmpty()) {
                removeMembers = null;
            }
            if (addAdmins.isEmpty()) {
                addAdmins = null;
            }
            if (removeAdmins.isEmpty()) {
                removeAdmins = null;
            }
            if (groupLinkState.isEmpty()) {
                groupLinkState = null;
            }
            if (addMemberPermission.isEmpty()) {
                addMemberPermission = null;
            }
            if (editDetailsPermission.isEmpty()) {
                editDetailsPermission = null;
            }
            if (avatar.isEmpty()) {
                avatarFile = null;
            } else {
                avatarFile = new File(avatar);
                //TODO: check if we are sending an empty file. If so, this tells Signal
                // to delete the avatar, so we should delete it from the local AvatarStore
                long fileSize = avatarFile.length();
                if (fileSize == 0) {
                    try {
                        if (avatarFile.exists()) {
                            Files.delete(avatarFile.toPath());
                        }
                    } catch (IOException e) {
                        throw new Error.Failure(e.getMessage());
                    }
                }
            }

            String localNumber = m.getSelfNumber();
            final var memberIdentifiers = getSingleRecipientIdentifiers(addMembers, localNumber);
            if (groupId == null) {
                final var results = m.createGroup(name, memberIdentifiers, avatar == null ? null : new File(avatar));
                checkSendMessageResults(results.second().getTimestamp(), results.second().getResults());
                return results.first().serialize();
            } else {
                Group group = null;
                try {
                    group = m.getGroup(getGroupId(groupId));
                } catch (AssertionError e) {
                    throw new Error.Failure(e.getMessage());
                }
                if (group == null) {
                    throw new Error.InvalidGroupId("GroupId is null.");
                }

                final var results = m.updateGroup(getGroupId(groupId),
                        name,
                        description,
                        getSingleRecipientIdentifiers(addMembers, localNumber),
                        getSingleRecipientIdentifiers(removeMembers, localNumber),
                        getSingleRecipientIdentifiers(addAdmins, localNumber),
                        getSingleRecipientIdentifiers(removeAdmins, localNumber),
                        resetGroupLink,
                        groupLinkState == null ? null : UpdateGroupCommand.getGroupLinkState(groupLinkState),
                        addMemberPermission == null ? null : UpdateGroupCommand.getGroupPermission(addMemberPermission),
                        editDetailsPermission == null ? null : UpdateGroupCommand.getGroupPermission(editDetailsPermission),
                        avatarFile,
                        expirationTimer,
                        isAnnouncementGroup
                        );
                if (results != null) {
                    checkSendMessageResults(results.getTimestamp(), results.getResults());
                }
                return groupId;
            }
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException | GroupSendingNotAllowedException e) {
            throw new Error.InvalidGroupId("Invalid group id: " + e.getMessage());
        } catch (UserErrorException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        }
    }

    @Override
    public String getGroupInviteUri(byte[] groupId) {
        Group group = null;
        GroupInviteLinkUrl groupInviteUri = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        }
        groupInviteUri = group.getGroupInviteLinkUrl();
        if (groupInviteUri == null) {
            return "";
        }
        return groupInviteUri.getUrl();
    }

    @Override
    public List<String> getGroupPendingMembers(final byte[] groupId) {
        Group group = null;
        List<String> members = new ArrayList<>();
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            for (RecipientAddress addr : group.getPendingMembers()) {
                members.add(addr.getNumber().orElse(""));
            }
            return members;
        }
    }

    @Override
    public List<String> getGroupRequestingMembers(final byte[] groupId) {
        Group group = null;
        List<String> members = new ArrayList<>();
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            for (RecipientAddress addr : group.getRequestingMembers()) {
                members.add(addr.getNumber().orElse(""));
            }
            return members;
        }
    }

    @Override
    public List<String> getGroupAdminMembers(final byte[] groupId) {
        Group group = null;
        List<String> members = new ArrayList<>();
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            for (RecipientAddress addr : group.getAdminMembers()) {
                members.add(addr.getNumber().orElse(""));
            }
            return members;
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
                    ? Optional.absent()
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
        return Stream.concat(m.getIdentities().stream().map(Identity::getRecipient),
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
            final var address = identity.getRecipient();
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
        GroupId group = null;
        try {
            group = getGroupId(groupId);
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        }
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
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            return group.isBlocked();
        }
    }

    @Override
    public boolean isMember(final byte[] groupId) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
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

    public List<String> updateMembers(final byte[] groupId, List<String>members, boolean addToMembers) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
            if (group == null) {
                throw new Error.InvalidGroupId("GroupId is null.");
            }
            var localNumber = m.getSelfNumber();
            if (addToMembers) {
                var results = m.updateGroup(getGroupId(groupId),
                        null,
                        null,
                        getSingleRecipientIdentifiers(members, localNumber),
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        isGroupAnnounceOnly(groupId)
                        );
            } else {
                var results = m.updateGroup(getGroupId(groupId),
                        null,
                        null,
                        null,
                        getSingleRecipientIdentifiers(members, localNumber),
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        isGroupAnnounceOnly(groupId)
                        );
            }
            return getGroupMembers(groupId);
        } catch (NotAGroupMemberException | AssertionError | IOException | AttachmentInvalidException | GroupSendingNotAllowedException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException e) {
            throw new Error.InvalidGroupId("Invalid group id: " + e.getMessage());
        }
    }

    @Override
    public void setGroupAnnounceOnly(byte[] groupId, boolean isAnnouncementGroup) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
            if (group == null) {
                throw new Error.InvalidGroupId("GroupId is null.");
            }
            var results = m.updateGroup(getGroupId(groupId),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    isAnnouncementGroup
                    );
        } catch (NotAGroupMemberException | AssertionError | IOException | AttachmentInvalidException | GroupSendingNotAllowedException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException e) {
            throw new Error.InvalidGroupId("Invalid group id: " + e.getMessage());
        }

    }

    @Override
    public boolean isGroupAnnounceOnly(byte[] groupId) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            return group.isAnnouncementGroup();
        }
    }

    @Override
    public boolean isAdmin(final byte[] groupId) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
        } catch (AssertionError e) {
            throw new Error.Failure(e.getMessage());
        }
        if (group == null) {
            throw new Error.InvalidGroupId("GroupId is null.");
        } else {
            return getGroupAdminMembers(groupId).contains(m.getSelfNumber());
        }
    }

    @Override
    public List<String> updateAdmins(final byte[] groupId, List<String>admins, boolean addToAdmins) {
        Group group = null;
        try {
            group = m.getGroup(getGroupId(groupId));
            if (group == null) {
                throw new Error.InvalidGroupId("GroupId is null.");
            }
            var localNumber = m.getSelfNumber();
            if (addToAdmins) {
                var results = m.updateGroup(getGroupId(groupId),
                        null,
                        null,
                        null,
                        null,
                        getSingleRecipientIdentifiers(admins, localNumber),
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        isGroupAnnounceOnly(groupId)
                        );
            } else {
                var results = m.updateGroup(getGroupId(groupId),
                        null,
                        null,
                        null,
                        null,
                        null,
                        getSingleRecipientIdentifiers(admins, localNumber),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        isGroupAnnounceOnly(groupId)
                        );
            }
            return getGroupAdminMembers(groupId);
        } catch (NotAGroupMemberException | AssertionError | IOException | AttachmentInvalidException | GroupSendingNotAllowedException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException e) {
            throw new Error.GroupNotFound("Group not found: " + e.getMessage());
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

    private byte[] nullIfEmpty(final byte[] array) {
        return array.length == 0 ? null : array;
    }

    private String nullIfEmpty(final String name) {
        return name.isEmpty() ? null : name;
    }

    private static String getDeviceObjectPath(String basePath, long deviceId) {
        return basePath + "/Devices/" + deviceId;
    }

    public class DbusSignalDeviceImpl extends DbusProperties implements Signal.Device {

        private final org.asamk.signal.manager.api.Device device;

        public DbusSignalDeviceImpl(final org.asamk.signal.manager.api.Device device) {
            super();
            super.addPropertiesHandler(new DbusInterfacePropertiesHandler("org.asamk.Signal.Device",
                    List.of(new DbusProperty<>("Id", device::getId),
                            new DbusProperty<>("Name",
                                    () -> device.getName() == null ? "" : device.getName(),
                                    this::setDeviceName),
                            new DbusProperty<>("Created", device::getCreated),
                            new DbusProperty<>("LastSeen", device::getLastSeen))));
            this.device = device;
        }

        @Override
        public String getObjectPath() {
            return getDeviceObjectPath(objectPath, device.getId());
        }

        @Override
        public void removeDevice() throws Error.Failure {
            try {
                m.removeLinkedDevices(device.getId());
                updateDevices();
            } catch (IOException e) {
                throw new Error.Failure(e.getMessage());
            }
        }

        @Override
        public String getDeviceName() {
            return device.getName();
        }

        @Override
        public void setDeviceName(String deviceName) {
            if (device.isThisDevice()) {
                try {
                    m.updateAccountAttributes(deviceName);
                    // update device list
                    updateDevices();
                } catch (IOException e) {
                    throw new Error.Failure(e.getMessage());
                }
            }
            device.setName(deviceName);
        }
    }
}
