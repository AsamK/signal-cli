package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.util.CommandUtil;
import org.asamk.signal.util.ErrorUtils;

import java.io.IOException;
import java.util.Map;

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
            final SendMessageResults results;
            if (type == null || "read".equals(type)) {
                results = m.sendReadReceipt(recipient, targetTimestamps);
            } else if ("viewed".equals(type)) {
                results = m.sendViewedReceipt(recipient, targetTimestamps);
            } else {
                throw new UserErrorException("Unknown receipt type: " + type);
            }
            outputResult(outputWriter, results.timestamp());
            ErrorUtils.handleSendMessageResults(results.results());
        } catch (IOException e) {
            throw new UserErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")");
        }
    }

    private void outputResult(final OutputWriter outputWriter, final long timestamp) {
        if (outputWriter instanceof PlainTextWriter writer) {
            writer.println("{}", timestamp);
        } else {
            final var writer = (JsonWriter) outputWriter;
            writer.write(Map.of("timestamp", timestamp));
        }
    }
}
