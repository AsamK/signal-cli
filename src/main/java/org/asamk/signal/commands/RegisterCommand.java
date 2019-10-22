package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class RegisterCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not sms.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        try {
            m.register(ns.getBoolean("voice"));
            return 0;
        } catch (IOException e) {
            System.err.println("Request verify error: " + e.getMessage());
            return 3;
        }
    }
}
