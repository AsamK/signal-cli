package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.UserAlreadyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class LinkCommand implements ProvisioningCommand {

    private final static Logger logger = LoggerFactory.getLogger(LinkCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-n", "--name").help("Specify a name to describe this new device.");
    }

    @Override
    public void handleCommand(final Namespace ns, final ProvisioningManager m) throws CommandException {
        final var writer = new PlainTextWriterImpl(System.out);

        var deviceName = ns.getString("name");
        if (deviceName == null) {
            deviceName = "cli";
        }
        try {
            writer.println("{}", m.getDeviceLinkUri());
            try (var manager = m.finishDeviceLink(deviceName)) {
                writer.println("Associated with: {}", manager.getUsername());
            }
        } catch (TimeoutException e) {
            throw new UserErrorException("Link request timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: " + e.getMessage());
        } catch (UserAlreadyExists e) {
            throw new UserErrorException("The user "
                    + e.getUsername()
                    + " already exists\nDelete \""
                    + e.getFileName()
                    + "\" before trying again.");
        }
    }
}
