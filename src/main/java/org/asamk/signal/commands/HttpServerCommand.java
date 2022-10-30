package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.http.HttpServerHandler;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.OutputWriter;

public class HttpServerCommand implements MultiLocalCommand {

    @Override
    public String getName() {
        return "http";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Takes commands via an http connection");
        subparser.addArgument("--port")
                .help("The port on which to open the HTTP service")
                .type(Integer.class)
                .setDefault(8080);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final MultiAccountManager m,
            final OutputWriter outputWriter
    ) throws CommandException {

        final var port = ns.getInt("port");

        final var handler = new HttpServerHandler();
        handler.init(port, m);

    }
}
