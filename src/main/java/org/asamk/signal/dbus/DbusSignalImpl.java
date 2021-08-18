package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.SignalControl;
import org.asamk.SignalControl.Error;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.GetUserStatusCommand;
import org.asamk.signal.commands.UpdateGroupCommand;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.GroupPermission;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;

import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class DbusSignalImpl implements Signal {

    private final Manager m;
    private final String objectPath;
    private final static Logger logger = LoggerFactory.getLogger(DbusSignalImpl.class);

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
    public void updateAccount() {
         try {
             m.updateAccountAttributes();
         } catch (IOException | Error.Failure e) {
             throw new Error.Failure("UpdateAccount error: " + e.getMessage());
         }
    }

    @Override
    public List<String> listIdentity(String number) {
        List<IdentityInfo> identities;
        IdentityInfo theirId;
        try {
            identities = m.getIdentities(number);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber("Invalid number: " + e.getMessage());
        }
        List<String> results = new ArrayList<String>();
        if (identities.isEmpty()) {return results;}
        theirId = identities.get(0);
        final SignalServiceAddress address = m.resolveSignalServiceAddress(theirId.getRecipientId());
        var digits = Util.formatSafetyNumber(m.computeSafetyNumber(address, theirId.getIdentityKey()));
        results.add(theirId.getTrustLevel().toString());
        results.add(theirId.getDateAdded().toString());
        results.add(Hex.toString(theirId.getFingerprint()));
        results.add(digits);
        return results;
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

          for (var d : devices) {
              var name = d.getName();
              if (name == null) {name = "null";}
              results.add(name);
          }
          return results;
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

    private static void checkSendMessageResults(
            long timestamp, List<SendMessageResult> results
    ) throws DBusExecutionException {
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
            final var results = m.sendRemoteDeleteMessage(targetSentTimestamp, recipients);
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public long sendGroupRemoteDeleteMessage(
            final long targetSentTimestamp, final byte[] groupId
    ) {
        try {
            final var results = m.sendGroupRemoteDeleteMessage(targetSentTimestamp, GroupId.unknownVersion(groupId));
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
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
            final var results = m.sendMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, recipients);
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        }
    }

    @Override
    public long sendNoteToSelfMessage(
            final String message, final List<String> attachments
    ) {
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
    @Override
    public long sendGroupMessage(final String message, final List<String> attachments, final String base64GroupId) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        return sendGroupMessage(message, attachments, groupId);
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
            final var results = m.sendGroupMessageReaction(emoji,
                    remove,
                    targetAuthor,
                    targetSentTimestamp,
                    GroupId.unknownVersion(groupId));
            checkSendMessageResults(results.first(), results.second());
            return results.first();
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessageReaction(
            final String emoji,
            final boolean remove,
            final String targetAuthor,
            final long targetSentTimestamp,
            final String base64GroupId
            ) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        return sendGroupMessageReaction(emoji, remove, targetAuthor, targetSentTimestamp, groupId);
    }

    @Override
    public void sendContacts() {
        try {
            m.sendContacts();
        } catch (UntrustedIdentityException e) {
            throw new Error.UntrustedIdentity("SendContacts error: " + e.getMessage());
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
    public void trust(String number, String safetyNumber){
        if (safetyNumber != null) {
            safetyNumber = safetyNumber.replaceAll(" ", "");
            if (safetyNumber.length() == 66) {
                byte[] fingerprintBytes;
                try {
                    fingerprintBytes = Hex.toByteArray(safetyNumber.toLowerCase(Locale.ROOT));
                } catch (Exception e) {
                    throw new Error.Failure(
                            "Failed to parse the fingerprint, make sure the fingerprint is a correctly encoded hex string without additional characters.");
                }
                boolean res;
                try {
                    res = m.trustIdentityVerified(number, fingerprintBytes);
                } catch (InvalidNumberException e) {
                    throw new Error.Failure("Failed to parse recipient: " + e.getMessage());
                }
                if (!res) {
                    throw new Error.Failure(
                            "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.");
                }
            } else if (safetyNumber.length() == 60) {
                boolean res;
                try {
                    res = m.trustIdentityVerifiedSafetyNumber(number, safetyNumber);
                } catch (InvalidNumberException e) {
                    throw new Error.InvalidNumber("Failed to parse recipient: " + e.getMessage());
                }
                if (!res) {
                    throw new Error.Failure(
                            "Failed to set the trust for the safety number of this phone number, make sure the phone number and the safety number are correct.");
                }
            } else {
                throw new Error.Failure(
                        "Safety number has invalid format, either specify the old hex fingerprint or the new safety number");
            }
        } else {
            throw new Error.Failure(
                    "You need to specify the fingerprint/safety number you have verified with -v SAFETY_NUMBER");
        }
    }

    @Override
    public void sendTyping(boolean typingAction, String base64GroupId, List<String>recipients) {
        final var noRecipients = recipients == null || recipients.isEmpty();
        final var noGroup = base64GroupId == null || base64GroupId.isEmpty();
        if (noRecipients && noGroup) {
            throw new Error.Failure("No recipients given");
        }
        if (!noRecipients && !noGroup) {
            throw new Error.Failure("You cannot specify recipients by phone number and groups at the same time");
        }

        final TypingAction action = typingAction ? TypingAction.START : TypingAction.STOP;

        GroupId groupId = null;
        if (!noGroup) {
            try {
                groupId = Util.decodeGroupId(base64GroupId);
            } catch (GroupIdFormatException e) {
                throw new Error.Failure("Invalid group id: " + e.getMessage());
            }
        }

        try {
            if (groupId != null) {
                m.sendGroupTypingMessage(action, groupId);
            } else {
                m.sendTypingMessage(action, new HashSet<String>(recipients));
            }
        } catch (UntrustedIdentityException e) {
            throw new Error.UntrustedIdentity("Failed to send message: " + e.getMessage());
        } catch (IOException e) {
            throw new Error.Failure("Failed to send message: " + e.getMessage());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.Failure("Failed to send to group: " + e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.Failure("Invalid number: " + e.getMessage());
        }
    }

    // Since contact names might be empty if not defined, also potentially return
    // the profile name
    @Override
    public String getContactName(final String number) {
        try {
            return m.getContactOrProfileName(number);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public void setContactName(final String number, final String name) {
        try {
            m.setContactName(number, name);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
        }
    }

    @Override
    public void setExpirationTimer(final String number, final int expiration) {
        try {
            m.setExpirationTimer(number, expiration);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
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
        } catch (NotMasterDeviceException e) {
            throw new Error.Failure("This command doesn't work on linked devices.");
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
    public void setGroupBlocked(final String base64GroupId, final boolean blocked) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        setGroupBlocked(groupId, blocked);
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
            return group.getMembers()
                    .stream()
                    .map(m::resolveSignalServiceAddress)
                    .map(Util::getLegacyIdentifier)
                    .collect(Collectors.toList());
        }
    }

    // To get the group Ids in base 64 format, either use the getBaseGroupIds() method, or
    //   the getGroupIds(dummy) form, where dummy represents any string
    @Override
    public List<String> getBase64GroupIds() {
        var groups = m.getGroups();
        var ids = new ArrayList<String>(groups.size());
        for (var group : groups) {
            ids.add(group.getGroupId().toBase64());
        }
        return ids;
    }

    @Override
    public List<String> getGroupIds(String dummy) {
        var groups = m.getGroups();
        var ids = new ArrayList<String>(groups.size());
        for (var group : groups) {
            ids.add(group.getGroupId().toBase64());
        }
        return ids;
    }

    @Override
    public String getGroupName(final String base64GroupId) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        var group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return "";
        } else {
            return group.getTitle();
        }
    }

    @Override
    public List<String> getGroupMembers(final String base64GroupId) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        var group = m.getGroup(GroupId.unknownVersion(groupId));
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
            if (members.isEmpty()) {
                members = null;
            }
            if (avatar.isEmpty()) {
                avatar = null;
            }
            if (groupId == null) {
                final var results = m.createGroup(name, members, avatar == null ? null : new File(avatar));
                checkSendMessageResults(0, results.second());
                return results.first().serialize();
            } else {
                final var results = m.updateGroup(GroupId.unknownVersion(groupId),
                        name,
                        null,
                        members,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        avatar == null ? null : new File(avatar),
                        null);
                if (results != null) {
                    checkSendMessageResults(results.first(), results.second());
                }
                return groupId;
            }
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
    public String updateGroup(String base64GroupId, String name, List<String> members, String avatar) {
        byte[] groupId = Base64.getDecoder().decode(base64GroupId);
        groupId = updateGroup(groupId, name, members, avatar);
        return Base64.getEncoder().encodeToString(groupId);
    }

    @Override
    public String updateGroup(
            String base64GroupId,
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
        try {
            byte[] groupId = null;
            File avatarFile = null;
            if (base64GroupId.isEmpty()) {
                throw new Error.GroupNotFound("No group specified.");
            } else {
                groupId = Base64.getDecoder().decode(base64GroupId);
            }
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
                //check if we are sending an empty file. If so, this tells Signal
                // to delete the avatar, and we will delete it from the local AvatarStore
                 long fileSize = avatarFile.length();
                 if (fileSize == 0) {
                     try {
                         AvatarStore.deleteGroupAvatar(GroupId.unknownVersion(groupId));
                     } catch (IOException e) {
                         throw new Error.Failure(e.getMessage());
                     }
                 }
            }
            if (groupId == null) {
                final var results = m.createGroup(name, addMembers, avatar == null ? null : new File(avatar));
                checkSendMessageResults(0, results.second());
                return Base64.getEncoder().encodeToString(results.first().serialize());
            } else {
                final var results = m.updateGroup(GroupId.unknownVersion(groupId),
                        name,
                        description,
                        addMembers,
                        removeMembers,
                        addAdmins,
                        removeAdmins,
                        resetGroupLink,
                        groupLinkState == null ? null : UpdateGroupCommand.getGroupLinkState(groupLinkState),
                        addMemberPermission == null ? null : UpdateGroupCommand.getGroupPermission(addMemberPermission),
                        editDetailsPermission == null ? null : UpdateGroupCommand.getGroupPermission(editDetailsPermission),
                        avatarFile,
                        expirationTimer
                        );
                if (results != null) {
                    checkSendMessageResults(results.first(), results.second());
                }
                return base64GroupId;
            }
        } catch (UserErrorException | IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch ( GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        } catch (AttachmentInvalidException e) {
            throw new Error.AttachmentInvalid(e.getMessage());
        }
    }

    @Override
    public boolean isRegistered(String number) {
        if (number.isEmpty()) {
            return false;
        }
        try {
            Map<String, Boolean> registered;
            List<String> numbers = new ArrayList<String>();
            numbers.add(number);
            registered = m.areUsersRegistered(new HashSet<String>(numbers));
            return registered.get(number);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public List<Boolean> isRegistered(List<String> numbers) {
        List<Boolean> results = new ArrayList<Boolean> ();
        if (numbers.isEmpty()) {
            return results;
        }
        try {
            Map<String, Boolean> registered;
            registered = m.areUsersRegistered(new HashSet<String>(numbers));
            for (String number : numbers) {
                results.add(registered.get(number));
            }
            return results;
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
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
    public void updateProfile(
            final String givenName,
            final String familyName,
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
            m.setProfile(givenName, familyName, about, aboutEmoji, avatarFile);
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
    public void setPin(String registrationLockPin)  {
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
        return DbusSignalControlImpl.version();
    }

    @Override
    public String link() {
        return DbusSignalControlImpl.link();
    }

    @Override
    public String link(String newDeviceName) {
        return DbusSignalControlImpl.link(newDeviceName);
    }

    @Override
    public void addDevice(String uri) {
        try {
            m.addDeviceLink(new URI(uri));
        } catch (IOException | InvalidKeyException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + "Add device link failed. " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + "Device link uri has invalid format: " + e.getMessage());
        }
    }

    @Override
    public void removeDevice(int deviceId) {
        try {
            m.removeLinkedDevices(deviceId);
        } catch (IOException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + "Error while removing device: " + e.getMessage());
        }
    }

    @Override
    public void register(
            String number, boolean voiceVerification
    ) {
        DbusSignalControlImpl.register(number, voiceVerification);
    }

    @Override
    public void registerWithCaptcha(
            String number, boolean voiceVerification, String captcha
    ) {
        DbusSignalControlImpl.registerWithCaptcha(number, voiceVerification, captcha);
    }

    @Override
    public void unregister() {
        try {
            m.unregister();
            logger.info("Unregister succeeded, exiting.\n");
            System.exit(0);
        } catch (IOException e) {
            throw new Error.Failure(e.getClass().getSimpleName() + "Unregister error: " + e.getMessage());
        }
    }

    @Override
    public void verify(String number, String verificationCode) {
        DbusSignalControlImpl.verify(number, verificationCode);
    }

    @Override
    public void verifyWithPin(String number, String verificationCode, String pin) {
        DbusSignalControlImpl.verifyWithPin(number, verificationCode, pin);
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
        var group = GroupId.unknownVersion(groupId);
        try {
            m.sendQuitGroupMessage(group, Set.of());
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
            throw new Error.GroupNotFound(e.getMessage());
        } catch (IOException | LastGroupAdminException e) {
            throw new Error.Failure(e.getMessage());
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public void joinGroup(final String groupLink) {
        try {
            final var linkUrl = GroupInviteLinkUrl.fromUri(groupLink);
            if (linkUrl == null) {
                throw new Error.Failure("Group link is invalid:");
            }
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
        try {
            return m.isContactBlocked(number);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
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
            return group.isMember(m.getSelfRecipientId());
        }
    }

    @Override
    public void uploadStickerPack(String stickerPackPath) {
        File path = new File(stickerPackPath);

        try {
            var url = m.uploadStickerPack(path);
        } catch (IOException e) {
            throw new Error.Failure("Upload error (maybe image size is too large):" + e.getMessage());
        } catch (StickerPackInvalidException e) {
            throw new Error.Failure("Invalid sticker pack: " + e.getMessage());
        }
    }
}
