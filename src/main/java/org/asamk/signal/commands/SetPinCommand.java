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

public class SetPinCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("registrationLockPin")
                .help("The registration lock PIN, that will be required for new registrations (resets after 7 days of inactivity)");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            var registrationLockPin = ns.getString("registrationLockPin");
            m.setRegistrationLockPin(Optional.of(registrationLockPin));
        } catch (UnauthenticatedResponseException e) {
            throw new UnexpectedErrorException("Set pin error failed with unauthenticated response: " + e.getMessage());
        } catch (IOException e) {
            throw new IOErrorException("Set pin error: " + e.getMessage());
        }
    }
}
