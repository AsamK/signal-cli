package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UntrustedKeyErrorException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

import java.io.IOException;

public class SendContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a synchronization message with the local contacts list to all linked devices.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            m.sendContacts();
        } catch (UntrustedIdentityException e) {
            throw new UntrustedKeyErrorException("SendContacts error: " + e.getMessage());
        } catch (IOException e) {
            throw new IOErrorException("SendContacts error: " + e.getMessage());
        }
    }
}
