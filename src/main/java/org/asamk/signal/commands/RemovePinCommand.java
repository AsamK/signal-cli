package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

public class RemovePinCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            m.setRegistrationLockPin(Optional.absent());
        } catch (UnauthenticatedResponseException e) {
            throw new UnexpectedErrorException("Remove pin failed with unauthenticated response: " + e.getMessage());
        } catch (IOException e) {
            throw new IOErrorException("Remove pin error: " + e.getMessage());
        }
    }
}
