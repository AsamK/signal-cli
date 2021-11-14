package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;

public class UpdateContactCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "updateContact";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Update the details of a given contact");
        subparser.addArgument("recipient").help("Contact number");
        subparser.addArgument("-n", "--name").help("New contact name");
        subparser.addArgument("-e", "--expiration").type(int.class).help("Set expiration time of messages (seconds)");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var recipientString = ns.getString("recipient");
        var recipient = CommandUtil.getSingleRecipientIdentifier(recipientString, m.getSelfNumber());

        try {
            var expiration = ns.getInt("expiration");
            if (expiration != null) {
                m.setExpirationTimer(recipient, expiration);
            }

            var name = ns.getString("name");
            if (name != null) {
                m.setContactName(recipient, name);
            }
        } catch (IOException e) {
            throw new IOErrorException("Update contact error: " + e.getMessage(), e);
        } catch (NotMasterDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        }
    }
}
