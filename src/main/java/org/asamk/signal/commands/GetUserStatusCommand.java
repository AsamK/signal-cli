package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputType;
import org.asamk.signal.PlainTextWriterImpl;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GetUserStatusCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(GetUserStatusCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number").help("Phone number").nargs("+");
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the global \"--output=json\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        // Setup the json object mapper
        var inJson = ns.get("output") == OutputType.JSON || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        // Get a map of registration statuses
        Map<String, Boolean> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(ns.getList("number")));
        } catch (IOException e) {
            logger.debug("Failed to check registered users", e);
            throw new IOErrorException("Unable to check if users are registered");
        }

        // Output
        if (inJson) {
            final var jsonWriter = new JsonWriter(System.out);

            var jsonUserStatuses = registered.entrySet()
                    .stream()
                    .map(entry -> new JsonUserStatus(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            jsonWriter.write(jsonUserStatuses);
        } else {
            final var writer = new PlainTextWriterImpl(System.out);

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
