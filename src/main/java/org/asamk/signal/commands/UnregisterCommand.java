package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class UnregisterCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Unregister the current device from the signal server.");
        subparser.addArgument("--delete-account")
                .help("Delete account completely from server. CAUTION: Only do this if you won't use this number again!")
                .action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            if (ns.getBoolean("delete-account")) {
                m.deleteAccount();
            } else {
                m.unregister();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOErrorException("Unregister error: " + e.getMessage());
        }
    }
}
