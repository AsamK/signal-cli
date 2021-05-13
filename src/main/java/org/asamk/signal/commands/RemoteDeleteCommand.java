package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.errors.UnknownObject;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.util.List;

public class RemoteDeleteCommand implements DbusCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Remotely delete a previously sent message.");
        subparser.addArgument("-t", "--target-timestamp")
                .required(true)
                .type(long.class)
                .help("Specify the timestamp of the message to delete.");
        subparser.addArgument("-g", "--group").help("Specify the recipient group ID.");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
    }

    @Override
    public void handleCommand(final Namespace ns, final Signal signal) throws CommandException {
        final List<String> recipients = ns.getList("recipient");
        final var groupIdString = ns.getString("group");

        final var noRecipients = recipients == null || recipients.isEmpty();
        if (noRecipients && groupIdString == null) {
            throw new UserErrorException("No recipients given");
        }
        if (!noRecipients && groupIdString != null) {
            throw new UserErrorException("You cannot specify recipients by phone number and groups at the same time");
        }

        final long targetTimestamp = ns.getLong("target-timestamp");

        final var writer = new PlainTextWriterImpl(System.out);

        byte[] groupId = null;
        if (groupIdString != null) {
            try {
                groupId = Util.decodeGroupId(groupIdString).serialize();
            } catch (GroupIdFormatException e) {
                throw new UserErrorException("Invalid group id: " + e.getMessage());
            }
        }

        try {
            long timestamp;
            if (groupId != null) {
                timestamp = signal.sendGroupRemoteDeleteMessage(targetTimestamp, groupId);
            } else {
                timestamp = signal.sendRemoteDeleteMessage(targetTimestamp, recipients);
            }
            writer.println("{}", timestamp);
        } catch (UnknownObject e) {
            throw new UserErrorException("Failed to find dbus object, maybe missing the -u flag: " + e.getMessage());
        } catch (Signal.Error.InvalidNumber e) {
            throw new UserErrorException("Invalid number: " + e.getMessage());
        } catch (Signal.Error.GroupNotFound e) {
            throw new UserErrorException("Failed to send to group: " + e.getMessage());
        } catch (DBusExecutionException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage());
        }
    }
}
