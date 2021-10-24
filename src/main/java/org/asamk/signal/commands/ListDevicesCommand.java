package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
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
import java.util.stream.Collectors;

public class ListDevicesCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ListDevicesCommand.class);

    @Override
    public String getName() {
        return "listDevices";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of linked devices.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        List<Device> devices;
        try {
            devices = m.getLinkedDevices();
        } catch (IOException e) {
            throw new IOErrorException("Failed to get linked devices: " + e.getMessage(), e);
        }

        if (outputWriter instanceof PlainTextWriter writer) {
            for (var d : devices) {
                writer.println("- Device {}{}:", d.id(), (d.isThisDevice() ? " (this device)" : ""));
                writer.indent(w -> {
                    w.println("Name: {}", d.name());
                    w.println("Created: {}", DateUtils.formatTimestamp(d.created()));
                    w.println("Last seen: {}", DateUtils.formatTimestamp(d.lastSeen()));
                });
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonDevices = devices.stream()
                    .map(d -> new JsonDevice(d.id(), d.name(), d.created(), d.lastSeen()))
                    .collect(Collectors.toList());
            writer.write(jsonDevices);
        }
    }

    private record JsonDevice(long id, String name, long createdTimestamp, long lastSeenTimestamp) {}
}
