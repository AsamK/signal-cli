package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.MultiAccountManager;

public interface MultiLocalCommand extends CliCommand {

    void handleCommand(Namespace ns, MultiAccountManager c, OutputWriter outputWriter) throws CommandException;
}
