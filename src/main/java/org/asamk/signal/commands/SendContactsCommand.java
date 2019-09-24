package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

import java.io.IOException;

public class SendContactsCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send contacts to the signal server.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            m.sendContacts();
            return 0;
        } catch (IOException | UntrustedIdentityException e) {
            System.err.println("SendContacts error: " + e.getMessage());
            return 3;
        }
    }
}
