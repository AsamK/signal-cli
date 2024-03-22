package org.asamk.signal.commands;

import com.fasterxml.jackson.annotation.JsonInclude;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.RateLimitErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.UserStatus;
import org.asamk.signal.manager.api.UsernameStatus;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

public class GetUserStatusCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(GetUserStatusCommand.class);

    @Override
    public String getName() {
        return "getUserStatus";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("recipient").help("Phone number").nargs("*");
        subparser.addArgument("--username").help("Specify the recipient username or username link.").nargs("*");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        // Get a map of registration statuses
        Map<String, UserStatus> registered;
        try {
            registered = m.getUserStatus(new HashSet<>(ns.getList("recipient")));
        } catch (RateLimitException e) {
            final var message = CommandUtil.getRateLimitMessage(e);
            throw new RateLimitErrorException(message, e);
        } catch (IOException e) {
            throw new IOErrorException("Unable to check if users are registered: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }

        final var usernames = ns.<String>getList("username");
        final var registeredUsernames = usernames == null
                ? Map.<String, UsernameStatus>of()
                : m.getUsernameStatus(new HashSet<>(usernames));

        // Output
        switch (outputWriter) {
            case JsonWriter writer -> {
                var jsonUserStatuses = Stream.concat(registered.entrySet().stream().map(entry -> {
                    final var number = entry.getValue().number();
                    final var uuid = entry.getValue().uuid();
                    return new JsonUserStatus(entry.getKey(),
                            number,
                            null,
                            uuid == null ? null : uuid.toString(),
                            uuid != null);
                }), registeredUsernames.entrySet().stream().map(entry -> {
                    final var username = entry.getValue().username();
                    final var uuid = entry.getValue().uuid();
                    return new JsonUserStatus(entry.getKey(),
                            null,
                            username,
                            uuid == null ? null : uuid.toString(),
                            uuid != null);
                })).toList();
                writer.write(jsonUserStatuses);
            }
            case PlainTextWriter writer -> {
                for (var entry : registered.entrySet()) {
                    final var userStatus = entry.getValue();
                    writer.println("{}: {}{}",
                            entry.getKey(),
                            userStatus.uuid() != null,
                            userStatus.unrestrictedUnidentifiedAccess() ? " (unrestricted sealed sender)" : "");
                }
                for (var entry : registeredUsernames.entrySet()) {
                    final var userStatus = entry.getValue();
                    writer.println("{}: {}{}",
                            entry.getKey(),
                            userStatus.uuid() != null,
                            userStatus.unrestrictedUnidentifiedAccess() ? " (unrestricted sealed sender)" : "");
                }
            }
        }
    }

    private record JsonUserStatus(
            String recipient,
            @JsonInclude(JsonInclude.Include.NON_NULL) String number,
            @JsonInclude(JsonInclude.Include.NON_NULL) String username,
            String uuid,
            boolean isRegistered
    ) {}
}
