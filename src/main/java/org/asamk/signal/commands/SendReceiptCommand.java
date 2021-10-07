package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;

public class SendReceiptCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendReceipt";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a read or viewed receipt to a previously received message.");
        subparser.addArgument("recipient").help("Specify the sender's phone number.").required(true);
        subparser.addArgument("-t", "--target-timestamp")
                .type(long.class)
                .nargs("+")
                .help("Specify the timestamp of the messages for which a receipt should be sent.");
        subparser.addArgument("--type")
                .help("Specify the receipt type (default is read receipt).")
                .choices("read", "viewed");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var recipientString = ns.getString("recipient");
        final var recipient = CommandUtil.getSingleRecipientIdentifier(recipientString, m.getSelfNumber());

        final var targetTimestamps = ns.<Long>getList("target-timestamp");
        final var type = ns.getString("type");

        try {
            if (type == null || "read".equals(type)) {
                m.sendReadReceipt(recipient, targetTimestamps);
            } else if ("viewed".equals(type)) {
                m.sendViewedReceipt(recipient, targetTimestamps);
            } else {
                throw new UserErrorException("Unknown receipt type: " + type);
            }
        } catch (IOException | UntrustedIdentityException e) {
            throw new UserErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")");
        }
    }
}
