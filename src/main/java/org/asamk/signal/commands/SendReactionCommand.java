package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotAGroupMemberException;
import org.asamk.signal.util.GroupIdFormatException;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;
import static org.asamk.signal.util.ErrorUtils.handleEncapsulatedExceptions;
import static org.asamk.signal.util.ErrorUtils.handleGroupIdFormatException;
import static org.asamk.signal.util.ErrorUtils.handleGroupNotFoundException;
import static org.asamk.signal.util.ErrorUtils.handleIOException;
import static org.asamk.signal.util.ErrorUtils.handleInvalidNumberException;
import static org.asamk.signal.util.ErrorUtils.handleNotAGroupMemberException;

public class SendReactionCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send reaction to a previously received or sent message.");
        subparser.addArgument("-g", "--group")
                .help("Specify the recipient group ID.");
        subparser.addArgument("recipient")
                .help("Specify the recipients' phone number.")
                .nargs("*");
        subparser.addArgument("-e", "--emoji")
                .required(true)
                .help("Specify the emoji, should be a single unicode grapheme cluster.");
        subparser.addArgument("-a", "--target-author")
                .required(true)
                .help("Specify the number of the author of the message to which to react.");
        subparser.addArgument("-t", "--target-timestamp")
                .required(true)
                .type(long.class)
                .help("Specify the timestamp of the message to which to react.");
        subparser.addArgument("-r", "--remove")
                .help("Remove a reaction.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        if ((ns.getList("recipient") == null || ns.getList("recipient").size() == 0) && ns.getString("group") == null) {
            System.err.println("No recipients given");
            System.err.println("Aborting sending.");
            return 1;
        }

        String emoji = ns.getString("emoji");
        boolean isRemove = ns.getBoolean("remove");
        String targetAuthor = ns.getString("target_author");
        long targetTimestamp = ns.getLong("target_timestamp");

        try {
            if (ns.getString("group") != null) {
                byte[] groupId = Util.decodeGroupId(ns.getString("group"));
                m.sendGroupMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, groupId);
            } else {
                m.sendMessageReaction(emoji, isRemove, targetAuthor, targetTimestamp, ns.getList("recipient"));
            }
            return 0;
        } catch (IOException e) {
            handleIOException(e);
            return 3;
        } catch (EncapsulatedExceptions e) {
            handleEncapsulatedExceptions(e);
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (GroupNotFoundException e) {
            handleGroupNotFoundException(e);
            return 1;
        } catch (NotAGroupMemberException e) {
            handleNotAGroupMemberException(e);
            return 1;
        } catch (GroupIdFormatException e) {
            handleGroupIdFormatException(e);
            return 1;
        } catch (InvalidNumberException e) {
            handleInvalidNumberException(e);
            return 1;
        }
    }
}
