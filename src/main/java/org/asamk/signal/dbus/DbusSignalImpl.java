package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.GroupId;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotAGroupMemberException;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.util.ErrorUtils;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);
        return sendMessage(message, attachments, recipients);
    }

    private static void checkSendMessageResults(
            long timestamp, List<SendMessageResult> results
    ) throws DBusExecutionException {
        List<String> errors = ErrorUtils.getErrorMessagesFromSendMessageResults(results);
        if (errors.size() == 0) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(timestamp).append('\n');
        message.append("Failed to send (some) messages:\n");
        for (String error : errors) {
            message.append(error).append('\n');
        }

        throw new Error.Failure(message.toString());
    }

    @Override
    public long sendMessage(final String message, final List<String> attachments, final List<String> recipients) {
        try {
            final Pair<Long, List<SendMessageResult>> results = m.sendMessage(message, attachments, recipients);
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
    public void sendEndSessionMessage(final List<String> recipients) {
        try {
            final Pair<Long, List<SendMessageResult>> results = m.sendEndSessionMessage(recipients);
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
            Pair<Long, List<SendMessageResult>> results = m.sendGroupMessage(message,
                    attachments,
                    GroupId.unknownVersion(groupId));
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
    public String getContactName(final String number) {
        try {
            return m.getContactName(number);
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
        List<GroupInfo> groups = m.getGroups();
        List<byte[]> ids = new ArrayList<>(groups.size());
        for (GroupInfo group : groups) {
            ids.add(group.getGroupId().serialize());
        }
        return ids;
    }

    @Override
    public String getGroupName(final byte[] groupId) {
        GroupInfo group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return "";
        } else {
            return group.getTitle();
        }
    }

    @Override
    public List<String> getGroupMembers(final byte[] groupId) {
        GroupInfo group = m.getGroup(GroupId.unknownVersion(groupId));
        if (group == null) {
            return Collections.emptyList();
        } else {
            return group.getMembers()
                    .stream()
                    .map(m::resolveSignalServiceAddress)
                    .map(SignalServiceAddress::getLegacyIdentifier)
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
            final Pair<GroupId, List<SendMessageResult>> results = m.updateGroup(groupId == null
                    ? null
                    : GroupId.unknownVersion(groupId), name, members, avatar);
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
}
