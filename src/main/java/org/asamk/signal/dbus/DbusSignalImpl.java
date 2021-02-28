package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.storage.protocol.IdentityInfo;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.manager.util.Utils;
import org.asamk.signal.BaseConfig;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public class DbusSignalImpl implements Signal {

    private final Manager m;

    public DbusSignalImpl(final Manager m) {
        this.m = m;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return null;
    }

    @Override
    public long sendMessage(final String message, final List<String> attachments, final String recipient) {
        var recipients = new ArrayList<String>(1);
        recipients.add(recipient);
        return sendMessage(message, attachments, recipients);
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

    private static void checkSendMessageResults(long timestamp, List<SendMessageResult> results)
            throws DBusExecutionException {
        if (results.size() == 1) {
            checkSendMessageResult(timestamp, results.get(0));
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

    @Override
    public long sendMessage(final String message, final List<String> attachments, final List<String> recipients) {
        try {
            final var results = m.sendMessage(message, attachments, recipients);
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public long sendNoteToSelfMessage(final String message, final List<String> attachments)
            throws Error.AttachmentInvalid, Error.Failure, Error.UntrustedIdentity {
        try {
            final var results = m.sendSelfMessage(message, attachments);
            checkSendMessageResult(results.first(), results.second());
            return results.first();
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void sendEndSessionMessage(final List<String> recipients) {
        try {
            final var results = m.sendEndSessionMessage(recipients);
            checkSendMessageResults(results.first(), results.second());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessage(final String message, final List<String> attachments, final byte[] groupId) {
        try {
            var results = m.sendGroupMessage(message, attachments, GroupId.unknownVersion(groupId));
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        }
    }

    // Since contact names might be empty if not defined, also potentially return
    // the profile name
    @Override
    public String getContactName(final String number) {
        String name="";
        try {
            name = m.getContactOrProfileName(number);
        } catch (Exception e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
        return name;
    }

    @Override
    public void setContactName(final String number, final String name) {
        try {
            m.setContactName(number, name);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public void setContactBlocked(final String number, final boolean blocked) {
        try {
            m.setContactBlocked(number, blocked);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public void setGroupBlocked(final byte[] groupId, final boolean blocked) {
        try {
            m.setGroupBlocked(GroupId.unknownVersion(groupId), blocked);
        } catch (GroupNotFoundException e) {
            throw new Error.GroupNotFound(e.getMessage());
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
        var group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return "";
        } else {
            return group.getTitle();
        }
    }

    @Override
    public List<String> getGroupMembers(final byte[] groupId) {
        var group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return List.of();
        } else {
            return group.getMembers().stream().map(m::resolveSignalServiceAddress)
                    .map(SignalServiceAddress::getLegacyIdentifier).collect(Collectors.toList());
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
            if (members.isEmpty()) {
                members = null;
            }
            if (avatar.isEmpty()) {
                avatar = null;
            }
            final var results = m.updateGroup(groupId == null ? null : GroupId.unknownVersion(groupId), name, members,
                    avatar == null ? null : new File(avatar));
            checkSendMessageResults(0, results.second());
            return results.first().serialize();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        }
    }

    @Override
    public boolean isRegistered() {
        return true;
    }

    @Override
    public void updateProfile(final String name, final String about, final String aboutEmoji, String avatarPath,
            final boolean removeAvatar) {
        try {
            if (avatarPath.isEmpty()) {
                avatarPath = null;
            }
            Optional<File> avatarFile = removeAvatar ? Optional.absent()
                    : avatarPath == null ? null : Optional.of(new File(avatarPath));
            m.setProfile(name, about, aboutEmoji, avatarFile);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
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
             return Stream.concat(m.getIdentities().stream().map(i -> i.getAddress().getNumber().orNull()),
                m.getContacts().stream().map(c -> c.number))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getContactNumber(final String name) {
        // Contact names have precendence.
        List<String> numbers=new ArrayList<>();
        var contacts = m.getContacts();
        for (var c : contacts) {
            if (c.name!=null && c.name.equals(name)) {
                numbers.add(c.number);
            }
        }
        // Try profiles if no contact name was found
        for (IdentityInfo identity : m.getIdentities()) {
            String number = identity.getAddress().getNumber().orNull();
            if (number != null) {
                var address = Utils.getSignalServiceAddressFromIdentifier(number);
                var profile = m.getRecipientProfile(address);
                String profileName = profile.getDisplayName();
                if (profileName.equals(name)) {
                    numbers.add(number);
                }
            }
        }
        if (numbers.size()==0) {
            throw new Error.Failure("Contact name not found");
        }
        return numbers;
    }

    @Override
    public void quitGroup(final byte[] groupId) {
        var group = GroupId.unknownVersion(groupId);
        try {
            m.sendQuitGroupMessage(group);
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public void joinGroup(final String groupLink) {
        final GroupInviteLinkUrl linkUrl;
        try {
            linkUrl = GroupInviteLinkUrl.fromUri(groupLink);
            m.joinGroup(linkUrl);
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
        var contacts = m.getContacts();
        for (var c : contacts) {
            if (c.number.equals(number)) {
                return c.blocked;
            }
        }
        return false;
    }

    @Override
    public boolean isGroupBlocked(final byte[] groupId) {
        var group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return false;
        } else {
            return group.isBlocked();
        }
    }

    @Override
    public boolean isMember(final byte[] groupId) {
        var group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return false;
        } else {
            return group.isMember(m.getSelfAddress());
        }
    }

}
