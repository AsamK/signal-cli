package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.GroupIdFormatException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.manager.Manager;
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

public class QuitGroupCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-g", "--group")
                .required(true)
                .help("Specify the recipient group ID.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        try {
            m.sendQuitGroupMessage(Util.decodeGroupId(ns.getString("group")));
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
