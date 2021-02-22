package org.asamk.signal.commands;

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
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            m.unregister();
        } catch (IOException e) {
            throw new IOErrorException("Unregister error: " + e.getMessage());
        }
    }
}
