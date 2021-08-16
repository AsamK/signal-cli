package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ListDevicesCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListDevicesCommand.class);
    private final OutputWriter outputWriter;

    public ListDevicesCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of linked devices.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        final var writer = (PlainTextWriter) outputWriter;

        List<Device> devices;
        try {
            devices = m.getLinkedDevices();
        } catch (IOException e) {
            logger.debug("Failed to get linked devices", e);
            throw new IOErrorException("Failed to get linked devices: " + e.getMessage());
        }

        for (var d : devices) {
            writer.println("- Device {}{}:", d.getId(), (d.getId() == m.getDeviceId() ? " (this device)" : ""));
            writer.indent(w -> {
                w.println("Id: {}", d.getId());
                w.println("Name: {}", d.getName());
                w.println("Created: {}", DateUtils.formatTimestamp(d.getCreated()));
                w.println("Last seen: {}", DateUtils.formatTimestamp(d.getLastSeen()));
            });
        }
    }
}
