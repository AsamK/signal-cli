package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class LinkCommand implements ProvisioningCommand {

    private final static Logger logger = LoggerFactory.getLogger(LinkCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--name").help("Specify a name to describe this new device.");
    }

    @Override
    public int handleCommand(final Namespace ns, final ProvisioningManager m) {
        final var writer = new PlainTextWriterImpl(System.out);

        var deviceName = ns.getString("name");
        if (deviceName == null) {
            deviceName = "cli";
        }
        try {
            writer.println("{}", m.getDeviceLinkUri());
            var username = m.finishDeviceLink(deviceName);
            writer.println("Associated with: {}", username);
        } catch (TimeoutException e) {
            System.err.println("Link request timed out, please try again.");
            return 3;
        } catch (IOException e) {
            System.err.println("Link request error: " + e.getMessage());
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return 2;
        } catch (UserAlreadyExists e) {
            System.err.println("The user "
                    + e.getUsername()
                    + " already exists\nDelete \""
                    + e.getFileName()
                    + "\" before trying again.");
            return 1;
        }
        return 0;
    }
}
