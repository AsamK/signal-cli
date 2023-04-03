package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.InvalidUsernameException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.io.IOException;

public class UpdateAccountCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "updateAccount";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Update the account attributes on the signal server.");
        subparser.addArgument("-n", "--device-name").help("Specify a name to describe this device.");
        var mut = subparser.addMutuallyExclusiveGroup();
        mut.addArgument("-u", "--username").help("Specify a username that can then be used to contact this account.");
        mut.addArgument("--delete-username")
                .action(Arguments.storeTrue())
                .help("Delete the username associated with this account.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var deviceName = ns.getString("device-name");
        try {
            m.updateAccountAttributes(deviceName);
        } catch (IOException e) {
            throw new IOErrorException("UpdateAccount error: " + e.getMessage(), e);
        }

        var username = ns.getString("username");
        if (username != null) {
            try {
                final var newUsername = m.setUsername(username);
                if (outputWriter instanceof PlainTextWriter w) {
                    w.println("Your new username: {}", newUsername);
                } else if (outputWriter instanceof JsonWriter w) {
                    w.write(new JsonAccountResponse(newUsername));
                }
            } catch (IOException e) {
                throw new IOErrorException("Failed to set username: " + e.getMessage(), e);
            } catch (InvalidUsernameException e) {
                throw new UserErrorException("Invalid username: " + e.getMessage(), e);
            }
        }

        var deleteUsername = Boolean.TRUE.equals(ns.getBoolean("delete-username"));
        if (deleteUsername) {
            try {
                m.deleteUsername();
            } catch (IOException e) {
                throw new IOErrorException("Failed to delete username: " + e.getMessage(), e);
            }
        }
    }

    private record JsonAccountResponse(
            String username
    ) {}
}
