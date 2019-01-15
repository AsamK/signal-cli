package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.DbusConfig.SIGNAL_BUSNAME;
import static org.asamk.signal.DbusConfig.SIGNAL_OBJECTPATH;
import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class DaemonCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        DBusConnection conn = null;
        try {
            try {
                int busType;
                String  busName;
                if (ns.getBoolean("system")) {
                    busType = DBusConnection.SYSTEM;
                } else {
                    busType = DBusConnection.SESSION;
                }
                conn = DBusConnection.getConnection(busType);
                conn.exportObject(SIGNAL_OBJECTPATH, m);
		busName = ns.getString("busname");
		if (busName == null) {
		    conn.requestBusName(SIGNAL_BUSNAME);
		} else {
		    conn.requestBusName(busName);
		}
            } catch (UnsatisfiedLinkError e) {
                System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                return 1;
            } catch (DBusException e) {
                e.printStackTrace();
                return 2;
            }
            boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
            try {
                m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, ns.getBoolean("json") ? new JsonDbusReceiveMessageHandler(m, conn, SIGNAL_OBJECTPATH) : new DbusReceiveMessageHandler(m, conn, SIGNAL_OBJECTPATH));
                return 0;
            } catch (IOException e) {
                System.err.println("Error while receiving messages: " + e.getMessage());
                return 3;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
