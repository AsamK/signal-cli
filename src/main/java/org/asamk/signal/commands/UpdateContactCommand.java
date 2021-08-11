package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

public class UpdateContactCommand implements JsonRpcLocalCommand {

    public UpdateContactCommand(final OutputWriter outputWriter) {
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Update the details of a given contact");
        subparser.addArgument("number").help("Contact number");
        subparser.addArgument("-n", "--name").help("New contact name");
        subparser.addArgument("-e", "--expiration").type(int.class).help("Set expiration time of messages (seconds)");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        var number = ns.getString("number");

        try {
            var expiration = ns.getInt("expiration");
            if (expiration != null) {
                m.setExpirationTimer(number, expiration);
            }

            var name = ns.getString("name");
            if (name != null) {
                m.setContactName(number, name);
            }
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid contact number: " + e.getMessage());
        } catch (IOException e) {
            throw new IOErrorException("Update contact error: " + e.getMessage());
        } catch (NotMasterDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        }
    }
}
