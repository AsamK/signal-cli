package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

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
            m.setRegistrationLockPin(Optional.absent());
        } catch (UnauthenticatedResponseException e) {
            throw new UnexpectedErrorException("Remove pin failed with unauthenticated response: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IOErrorException("Remove pin error: " + e.getMessage(), e);
        }
    }
}
