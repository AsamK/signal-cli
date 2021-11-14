package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;

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
    }
}
