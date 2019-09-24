package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;

public class SetContactNameCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number")
                .help("Contact number");
        subparser.addArgument("name")
                .help("New contact name");
        subparser.help("Set the name of a given contact");
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
