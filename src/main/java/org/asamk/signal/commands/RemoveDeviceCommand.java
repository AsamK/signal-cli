package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class RemoveDeviceCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-d", "--deviceId")
                .type(int.class)
                .required(true)
                .help("Specify the device you want to remove. Use listDevices to see the deviceIds.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        try {
            int deviceId = ns.getInt("deviceId");
            m.removeLinkedDevices(deviceId);
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            return 3;
        }
    }
}
