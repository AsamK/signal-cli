package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class UnregisterCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Unregister the current device from the signal server.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            m.unregister();
            return 0;
        } catch (IOException e) {
            System.err.println("Unregister error: " + e.getMessage());
            return 3;
        }
    }
}
