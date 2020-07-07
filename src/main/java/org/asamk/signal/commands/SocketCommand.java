package org.asamk.signal.commands;

import java.util.concurrent.TimeUnit;
import java.io.IOException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.JsonReceiveMessageHandler;
import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

import org.asamk.signal.socket.SocketTasks;

public class SocketCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-a", "--address")
                .setDefault("127.0.0.1")
                .help("Socket bind address");
        subparser.addArgument("-p", "--port")
                .type(int.class)
                .setDefault(24250)
                .help("Socket port to use");
        subparser.help("Receive at a socket while being able to send as well");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        System.out.println("Starting socket thread...");
        m.socketTasks = new SocketTasks(ns, m);
        try {
            Thread thread = new Thread(m.socketTasks);
            thread.start();
        } catch (Exception e) {
            System.err.println(e);
        }
        try {
            // Let the thread settle for a second
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {}
        System.out.println("Listening from Signal server...");

        final long timeout = 3600 * 1000; // timeout in ms
        final boolean returnOnTimeout = false;
        final boolean ignoreAttachments = true;
        try {
            final Manager.ReceiveMessageHandler handler = new JsonReceiveMessageHandler(m);
            while(true) {
                m.receiveMessages(timeout, TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, handler);
            }
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
}
