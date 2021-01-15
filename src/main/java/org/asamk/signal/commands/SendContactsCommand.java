package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

import java.io.IOException;

public class SendContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a synchronization message with the local contacts list to all linked devices.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        try {
            m.sendContacts();
            return 0;
        } catch (UntrustedIdentityException e) {
            System.err.println("SendContacts error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("SendContacts error: " + e.getMessage());
            return 3;
        }
    }
}
