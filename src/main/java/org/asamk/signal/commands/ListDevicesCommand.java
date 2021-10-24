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
                writer.println("- Device {}{}:", d.getId(), (d.isThisDevice() ? " (this device)" : ""));
                writer.indent(w -> {
                    w.println("Name: {}", d.getName());
                    w.println("Created: {}", DateUtils.formatTimestamp(d.getCreated()));
                    w.println("Last seen: {}", DateUtils.formatTimestamp(d.getLastSeen()));
                });
            }
        } else {
            final var writer = (JsonWriter) outputWriter;
            final var jsonDevices = devices.stream()
                    .map(d -> new JsonDevice(d.getId(), d.getName(), d.getCreated(), d.getLastSeen()))
                    .collect(Collectors.toList());
            writer.write(jsonDevices);
        }
    }

    private static final class JsonDevice {

        public final long id;
        public final String name;
        public final long createdTimestamp;
        public final long lastSeenTimestamp;

        private JsonDevice(
                final long id, final String name, final long createdTimestamp, final long lastSeenTimestamp
        ) {
            this.id = id;
            this.name = name;
            this.createdTimestamp = createdTimestamp;
            this.lastSeenTimestamp = lastSeenTimestamp;
        }
    }
}
