package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class RemoveDeviceCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-d", "--device-id", "--deviceId")
                .type(int.class)
                .required(true)
                .help("Specify the device you want to remove. Use listDevices to see the deviceIds.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        try {
            int deviceId = ns.getInt("device-id");
            m.removeLinkedDevices(deviceId);
        } catch (IOException e) {
            throw new IOErrorException("Error while removing device: " + e.getMessage());
        }
    }
}
