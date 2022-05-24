package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class SendSyncRequestCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendSyncRequest";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a synchronization request message to primary device (for group, contacts, ...).");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        try {
            m.requestAllSyncData();
        } catch (IOException e) {
            throw new IOErrorException("Request sync data error: " + e.getMessage(), e);
        }
    }
}
