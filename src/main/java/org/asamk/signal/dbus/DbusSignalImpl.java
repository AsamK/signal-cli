package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotAGroupMemberException;
import org.asamk.signal.storage.groups.GroupInfo;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private static DBusExecutionException convertEncapsulatedExceptions(EncapsulatedExceptions e) {
        if (e.getNetworkExceptions().size() + e.getUnregisteredUserExceptions().size() + e.getUntrustedIdentityExceptions().size() == 1) {
            if (e.getNetworkExceptions().size() == 1) {
                NetworkFailureException n = e.getNetworkExceptions().get(0);
                return new Error.Failure("Network failure for \"" + n.getE164number() + "\": " + n.getMessage());
            } else if (e.getUnregisteredUserExceptions().size() == 1) {
                UnregisteredUserException n = e.getUnregisteredUserExceptions().get(0);
                return new Error.UnregisteredUser("Unregistered user \"" + n.getE164Number() + "\": " + n.getMessage());
            } else if (e.getUntrustedIdentityExceptions().size() == 1) {
                UntrustedIdentityException n = e.getUntrustedIdentityExceptions().get(0);
                return new Error.UntrustedIdentity("Untrusted Identity for \"" + n.getIdentifier() + "\": " + n.getMessage());
            }
        }

        StringBuilder message = new StringBuilder();
        message.append("Failed to send (some) messages:").append('\n');
        for (NetworkFailureException n : e.getNetworkExceptions()) {
            message.append("Network failure for \"").append(n.getE164number()).append("\": ").append(n.getMessage()).append('\n');
        }
        for (UnregisteredUserException n : e.getUnregisteredUserExceptions()) {
            message.append("Unregistered user \"").append(n.getE164Number()).append("\": ").append(n.getMessage()).append('\n');
        }
        for (UntrustedIdentityException n : e.getUntrustedIdentityExceptions()) {
            message.append("Untrusted Identity for \"").append(n.getIdentifier()).append("\": ").append(n.getMessage()).append('\n');
        }

        return new Error.Failure(message.toString());
    }

    @Override
    public long sendMessage(final String message, final List<String> attachments, final List<String> recipients) {
        try {
            return m.sendMessage(message, attachments, recipients);
        } catch (EncapsulatedExceptions e) {
            throw convertEncapsulatedExceptions(e);
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
            m.sendEndSessionMessage(recipients);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (EncapsulatedExceptions e) {
            throw convertEncapsulatedExceptions(e);
        } catch (InvalidNumberException e) {
            throw new Error.InvalidNumber(e.getMessage());
        }
    }

    @Override
    public long sendGroupMessage(final String message, final List<String> attachments, final byte[] groupId) {
        try {
            return m.sendGroupMessage(message, attachments, groupId);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (EncapsulatedExceptions e) {
            throw convertEncapsulatedExceptions(e);
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
            m.setGroupBlocked(groupId, blocked);
        } catch (GroupNotFoundException e) {
            throw new Error.GroupNotFound(e.getMessage());
        }
    }

    @Override
    public List<byte[]> getGroupIds() {
        List<GroupInfo> groups = m.getGroups();
        List<byte[]> ids = new ArrayList<>(groups.size());
        for (GroupInfo group : groups) {
            ids.add(group.groupId);
        }
        return ids;
    }

    @Override
    public String getGroupName(final byte[] groupId) {
        GroupInfo group = m.getGroup(groupId);
        if (group == null) {
            return "";
        } else {
            return group.name;
        }
    }

    @Override
    public List<String> getGroupMembers(final byte[] groupId) {
        GroupInfo group = m.getGroup(groupId);
        if (group == null) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(group.getMembersE164());
        }
    }

    @Override
    public byte[] updateGroup(final byte[] groupId, final String name, final List<String> members, final String avatar) {
        try {
            return m.updateGroup(groupId, name, members, avatar);
        } catch (IOException e) {
            throw new Error.Failure(e.getMessage());
        } catch (EncapsulatedExceptions e) {
            throw convertEncapsulatedExceptions(e);
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
