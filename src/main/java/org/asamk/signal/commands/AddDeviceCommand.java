package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class AddDeviceCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--uri")
                .required(true)
                .help("Specify the uri contained in the QR code shown by the new device.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            m.addDeviceLink(new URI(ns.getString("uri")));
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 3;
        } catch (InvalidKeyException | URISyntaxException e) {
            e.printStackTrace();
            return 2;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
}
