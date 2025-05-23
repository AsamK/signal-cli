package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
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
        subparser.addArgument("--given-name").help("New system given name");
        subparser.addArgument("--family-name").help("New system family name");
        subparser.addArgument("--nick-given-name").help("New nick given name");
        subparser.addArgument("--nick-family-name").help("New nick family name");
        subparser.addArgument("--note").help("New note");
        subparser.addArgument("-e", "--expiration").type(int.class).help("Set expiration time of messages (seconds)");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        var recipientString = ns.getString("recipient");
        var recipient = CommandUtil.getSingleRecipientIdentifier(recipientString, m.getSelfNumber());

        try {
            var expiration = ns.getInt("expiration");
            if (expiration != null) {
                m.setExpirationTimer(recipient, expiration);
            }

            var givenName = ns.getString("given-name");
            var familyName = ns.getString("family-name");
            if (givenName == null) {
                givenName = ns.getString("name");
                if (givenName != null && familyName == null) {
                    familyName = "";
                }
            }
            var nickGivenName = ns.getString("nick-given-name");
            var nickFamilyName = ns.getString("nick-family-name");
            var note = ns.getString("note");
            if (givenName != null
                    || familyName != null
                    || nickGivenName != null
                    || nickFamilyName != null
                    || note != null) {
                m.setContactName(recipient, givenName, familyName, nickGivenName, nickFamilyName, note);
            }
        } catch (IOException e) {
            throw new IOErrorException("Update contact error: " + e.getMessage(), e);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        }
    }
}
