package org.asamk.signal.commands;

import java.io.IOException;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;

public class SetProfileNameCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("name")
                .help("New profile name");
        subparser.help("Set a new name for this profile");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        String name = ns.getString("name");

        try {
            m.setProfileName(name);
        } catch (IOException e) {
            System.err.println("UpdateAccount error: " + e.getMessage());
            return 3;
        }

        return 0;
    }

}
