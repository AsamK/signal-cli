package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

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
        Map<String, UserStatus> registered;
        try {
            registered = m.getUserStatus(new HashSet<>(ns.getList("recipient")));
        } catch (IOException e) {
            throw new IOErrorException("Unable to check if users are registered: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }

        // Output
        if (outputWriter instanceof JsonWriter jsonWriter) {

            var jsonUserStatuses = registered.entrySet().stream().map(entry -> {
                final var number = entry.getValue().number();
                final var uuid = entry.getValue().uuid();
                return new JsonUserStatus(entry.getKey(), number, uuid == null ? null : uuid.toString(), uuid != null);
            }).toList();

            jsonWriter.write(jsonUserStatuses);
        } else {
            final var writer = (PlainTextWriter) outputWriter;

            for (var entry : registered.entrySet()) {
                final var userStatus = entry.getValue();
                writer.println("{}: {}{}",
                        entry.getKey(),
                        userStatus.uuid() != null,
                        userStatus.unrestrictedUnidentifiedAccess() ? " (unrestricted sealed sender)" : "");
            }
        }
    }

    private record JsonUserStatus(String recipient, String number, String uuid, boolean isRegistered) {}
}
