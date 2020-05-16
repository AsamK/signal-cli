package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.IOException;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class UpdateContactCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number")
                .help("Contact number");
        subparser.addArgument("-n", "--name")
                .required(true)
                .help("New contact name");
        subparser.addArgument("-e", "--expiration")
                .required(false)
                .type(int.class)
                .help("Set expiration time of messages (seconds)");
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

        try {
            m.setContactName(number, name);

            Integer expiration = ns.getInt("expiration");
            if (expiration != null) {
                m.setExpirationTimer(number, expiration);
            }
        } catch (InvalidNumberException e) {
            System.out.println("Invalid contact number: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Update contact error: " + e.getMessage());
            return 3;
        }

        return 0;
    }
}
