package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.DeviceLimitExceededException;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.output.OutputWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class AddDeviceCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(AddDeviceCommand.class);

    @Override
    public String getName() {
        return "addDevice";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Link another device to this device. Only works, if this is the primary device.");
        subparser.addArgument("--uri")
                .required(true)
                .help("Specify the uri contained in the QR code shown by the new device.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final URI linkUri;
        try {
            linkUri = new URI(ns.getString("uri"));
        } catch (URISyntaxException e) {
            throw new UserErrorException("Device link uri has invalid format: " + e.getMessage());
        }

        try {
            var deviceLinkUrl = DeviceLinkUrl.parseDeviceLinkUri(linkUri);
            m.addDeviceLink(deviceLinkUrl);
        } catch (IOException e) {
            logger.error("Add device link failed: {}", e.getMessage());
            throw new IOErrorException("Add device link failed", e);
        } catch (InvalidDeviceLinkException e) {
            logger.info("Invalid device link");
            throw new UserErrorException("Invalid device link", e);
        } catch (DeviceLimitExceededException e) {
            throw new UserErrorException("Account has too many linked devices already", e);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        }
    }
}
