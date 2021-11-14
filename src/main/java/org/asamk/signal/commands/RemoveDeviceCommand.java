package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class RemoveDeviceCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "removeDevice";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Remove a linked device.");
        subparser.addArgument("-d", "--device-id", "--deviceId")
                .type(long.class)
                .required(true)
                .help("Specify the device you want to remove. Use listDevices to see the deviceIds.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        try {
            final var deviceId = ns.getLong("device-id");
            m.removeLinkedDevices(deviceId);
        } catch (IOException e) {
            throw new IOErrorException("Error while removing device: " + e.getMessage(), e);
        }
    }
}
