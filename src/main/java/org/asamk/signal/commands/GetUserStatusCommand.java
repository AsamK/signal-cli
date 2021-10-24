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
import org.whispersystems.libsignal.util.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GetUserStatusCommand implements JsonRpcLocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(GetUserStatusCommand.class);

    @Override
    public String getName() {
        return "getUserStatus";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("recipient").help("Phone number").nargs("+");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        // Get a map of registration statuses
        Map<String, Pair<String, UUID>> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(ns.getList("recipient")));
        } catch (IOException e) {
            throw new IOErrorException("Unable to check if users are registered", e);
        }

        // Output
        if (outputWriter instanceof JsonWriter jsonWriter) {

            var jsonUserStatuses = registered.entrySet().stream().map(entry -> {
                final var number = entry.getValue().first();
                final var uuid = entry.getValue().second();
                return new JsonUserStatus(entry.getKey(), number, uuid == null ? null : uuid.toString(), uuid != null);
            }).collect(Collectors.toList());

            jsonWriter.write(jsonUserStatuses);
        } else {
            final var writer = (PlainTextWriter) outputWriter;

            for (var entry : registered.entrySet()) {
                final var uuid = entry.getValue().second();
                writer.println("{}: {}", entry.getKey(), uuid != null);
            }
        }
    }

    private static final class JsonUserStatus {

        public final String recipient;

        public final String number;

        public final String uuid;

        public final boolean isRegistered;

        public JsonUserStatus(String recipient, String number, String uuid, boolean isRegistered) {
            this.recipient = recipient;
            this.number = number;
            this.uuid = uuid;
            this.isRegistered = isRegistered;
        }
    }
}
