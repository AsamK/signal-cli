package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class SendContactsCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendContacts";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a synchronization message with the local contacts list to all linked devices.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        try {
            m.sendContacts();
        } catch (IOException e) {
            throw new IOErrorException("SendContacts error: " + e.getMessage(), e);
        }
    }
}
