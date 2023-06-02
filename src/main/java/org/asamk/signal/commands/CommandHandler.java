package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.OutputWriter;

public class CommandHandler {

    final Namespace ns;
    final OutputWriter outputWriter;

    public CommandHandler(final Namespace ns, final OutputWriter outputWriter) {
        this.ns = ns;
        this.outputWriter = outputWriter;
    }

    public void handleProvisioningCommand(
            final ProvisioningCommand command, final ProvisioningManager provisioningManager
    ) throws CommandException {
        command.handleCommand(ns, provisioningManager, outputWriter);
    }

    public void handleRegistrationCommand(
            final RegistrationCommand command, final RegistrationManager registrationManager
    ) throws CommandException {
        command.handleCommand(ns, registrationManager);
    }

    public void handleLocalCommand(final LocalCommand command, final Manager manager) throws CommandException {
        command.handleCommand(ns, manager, outputWriter);
    }

    public void handleMultiLocalCommand(
            final MultiLocalCommand command, MultiAccountManager multiAccountManager
    ) throws CommandException {
        command.handleCommand(ns, multiAccountManager, outputWriter);
    }
}
