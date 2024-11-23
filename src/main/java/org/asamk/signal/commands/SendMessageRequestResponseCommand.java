package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope.Sync.MessageRequestResponse.Type;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

public class SendMessageRequestResponseCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendMessageRequestResponse";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send response to a message request to linked devices.");
        subparser.addArgument("-g", "--group-id", "--group").help("Specify the recipient group ID.").nargs("*");
        subparser.addArgument("recipient").help("Specify the recipients' phone number.").nargs("*");
        subparser.addArgument("-u", "--username").help("Specify the recipient username or username link.").nargs("*");
        subparser.addArgument("--type")
                .help("Type of message request response")
                .type(Arguments.enumStringType(MessageRequestResponseType.class))
                .required(true);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var recipientStrings = ns.<String>getList("recipient");
        final var groupIdStrings = ns.<String>getList("group-id");
        final var usernameStrings = ns.<String>getList("username");
        final var typeObj = ns.get("type");
        final var type = typeObj instanceof MessageRequestResponseType t
                ? t
                : MessageRequestResponseType.valueOf(((String) typeObj).toUpperCase());

        final var recipientIdentifiers = CommandUtil.getRecipientIdentifiers(m,
                false,
                recipientStrings,
                groupIdStrings,
                usernameStrings);
        m.sendMessageRequestResponse(type == MessageRequestResponseType.ACCEPT ? Type.ACCEPT : Type.DELETE,
                recipientIdentifiers);
    }
}
