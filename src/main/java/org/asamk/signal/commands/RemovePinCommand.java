package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

public class RemovePinCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        try {
            m.setRegistrationLockPin(Optional.absent());
            return 0;
        } catch (UnauthenticatedResponseException e) {
            System.err.println("Remove pin error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("Remove pin error: " + e.getMessage());
            return 3;
        }
    }
}
