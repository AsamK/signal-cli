package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class UpdateDeviceCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "updateDevice";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Update a linked device.");
        subparser.addArgument("-d", "--device-id", "--deviceId")
                .type(int.class)
                .required(true)
                .help("Specify the device you want to update. Use listDevices to see the deviceIds.");
        subparser.addArgument("-n", "--device-name")
                .required(true)
                .help("Specify a name to describe the given device.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        try {
            final var deviceId = ns.getInt("device-id");
            final var deviceName = ns.getString("device-name");
            m.updateLinkedDevice(deviceId, deviceName);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new IOErrorException("Error while updating device: " + e.getMessage(), e);
        }
    }
}
