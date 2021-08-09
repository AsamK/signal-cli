package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class GetUserStatusCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(GetUserStatusCommand.class);
    private final OutputWriter outputWriter;

    public static void attachToSubparser(final Subparser subparser) {
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("number").help("Phone number").nargs("+");
    }

    public GetUserStatusCommand(final OutputWriter outputWriter) {
        this.outputWriter = outputWriter;
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        // Get a map of registration statuses
        Map<String, Boolean> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(ns.getList("number")));
        } catch (IOException e) {
            logger.debug("Failed to check registered users", e);
            throw new IOErrorException("Unable to check if users are registered");
        }

        // Output
        if (outputWriter instanceof JsonWriter) {
            final var jsonWriter = (JsonWriter) outputWriter;

            var jsonUserStatuses = registered.entrySet()
                    .stream()
                    .map(entry -> new JsonUserStatus(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            jsonWriter.write(jsonUserStatuses);
        } else {
            final var writer = (PlainTextWriter) outputWriter;

            for (var entry : registered.entrySet()) {
                writer.println("{}: {}", entry.getKey(), entry.getValue());
            }
        }
    }

    private static final class JsonUserStatus {

        public String name;

        public boolean isRegistered;

        public JsonUserStatus(String name, boolean isRegistered) {
            this.name = name;
            this.isRegistered = isRegistered;
        }
    }
}
