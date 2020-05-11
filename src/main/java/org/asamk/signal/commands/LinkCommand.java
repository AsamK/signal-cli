package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.UserAlreadyExists;
import org.asamk.signal.manager.ProvisioningManager;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class LinkCommand implements ProvisioningCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--name")
                .help("Specify a name to describe this new device.");
    }

    @Override
    public int handleCommand(final Namespace ns, final ProvisioningManager m) {
        String deviceName = ns.getString("name");
        if (deviceName == null) {
            deviceName = "cli";
        }
        try {
            System.out.println(m.getDeviceLinkUri());
            String username = m.finishDeviceLink(deviceName);
            System.out.println("Associated with: " + username);
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
            System.err.println("The user " + e.getUsername() + " already exists\nDelete \"" + e.getFileName() + "\" before trying again.");
            return 1;
        }
        return 0;
    }
}
