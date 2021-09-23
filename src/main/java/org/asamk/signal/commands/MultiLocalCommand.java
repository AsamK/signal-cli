package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;

import java.util.List;

public interface MultiLocalCommand extends LocalCommand {

    void handleCommand(
            Namespace ns, List<Manager> managers, SignalCreator c, OutputWriter outputWriter
    ) throws CommandException;

    void handleCommand(
            Namespace ns, Manager m, SignalCreator c, OutputWriter outputWriter, TrustNewIdentity trustNewIdentity
    ) throws CommandException;

    public void handleCommand(
            Namespace ns, List<Manager> managers, SignalCreator c, OutputWriter outputWriter, TrustNewIdentity trustNewIdentity
    ) throws CommandException;

    @Override
    default void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        handleCommand(ns, List.of(m), null, outputWriter);
    }
}
