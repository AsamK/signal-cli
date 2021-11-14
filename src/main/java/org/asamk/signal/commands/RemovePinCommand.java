package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;
import java.util.Optional;

public class RemovePinCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "removePin";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Remove the registration lock pin.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        try {
            m.setRegistrationLockPin(Optional.empty());
        } catch (IOException e) {
            throw new IOErrorException("Remove pin error: " + e.getMessage(), e);
        }
    }
}
