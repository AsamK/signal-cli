package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class UpdateAccountCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Update the account attributes on the signal server.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        try {
            m.updateAccountAttributes();
            return 0;
        } catch (IOException e) {
            System.err.println("UpdateAccount error: " + e.getMessage());
            return 3;
        }
    }
}
