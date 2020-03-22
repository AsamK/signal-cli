package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

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
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            String registrationLockPin = ns.getString("registrationLockPin");
            m.setRegistrationLockPin(Optional.of(registrationLockPin));
            return 0;
        } catch (IOException | UnauthenticatedResponseException e) {
            System.err.println("Set pin error: " + e.getMessage());
            return 3;
        }
    }
}
