package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

public class UpdateContactCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number").help("Contact number");
        subparser.addArgument("-n", "--name").required(true).help("New contact name");
        subparser.addArgument("-e", "--expiration")
                .required(false)
                .type(int.class)
                .help("Set expiration time of messages (seconds)");
        subparser.help("Update the details of a given contact");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        var number = ns.getString("number");
        var name = ns.getString("name");

        try {
            m.setContactName(number, name);

            var expiration = ns.getInt("expiration");
            if (expiration != null) {
                m.setExpirationTimer(number, expiration);
            }
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid contact number: " + e.getMessage());
        } catch (IOException e) {
            throw new IOErrorException("Update contact error: " + e.getMessage());
        }
    }
}
