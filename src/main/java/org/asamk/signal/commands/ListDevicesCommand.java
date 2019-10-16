package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.util.List;

public class ListDevicesCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            List<DeviceInfo> devices = m.getLinkedDevices();
            for (DeviceInfo d : devices) {
                System.out.println("Device " + d.getId() + (d.getId() == m.getDeviceId() ? " (this device)" : "") + ":");
                System.out.println(" Name: " + d.getName());
                System.out.println(" Created: " + DateUtils.formatTimestamp(d.getCreated()));
                System.out.println(" Last seen: " + DateUtils.formatTimestamp(d.getLastSeen()));
            }
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 3;
        }
    }
}
