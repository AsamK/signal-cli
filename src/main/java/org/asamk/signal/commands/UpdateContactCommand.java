package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

public class UpdateContactCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number")
                .help("Contact number");
        subparser.addArgument("-n", "--name")
                .required(true)
                .help("New contact name");
        subparser.help("Update the details of a given contact");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        String number = ns.getString("number");
        String name = ns.getString("name");

        m.setContactName(number, name);

        return 0;
    }
}
