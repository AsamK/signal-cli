package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;

import java.io.IOException;

public class ListDevicesCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        final var writer = new PlainTextWriterImpl(System.out);
        try {
            var devices = m.getLinkedDevices();
            for (var d : devices) {
                writer.println("- Device {}{}:", d.getId(), (d.getId() == m.getDeviceId() ? " (this device)" : ""));
                writer.indent(w -> {
                    w.println("Name: {}", d.getName());
                    w.println("Created: {}", DateUtils.formatTimestamp(d.getCreated()));
                    w.println("Last seen: {}", DateUtils.formatTimestamp(d.getLastSeen()));
                });
            }
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 3;
        }
    }
}
