package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;
import java.util.Base64;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

public class SendPaymentNotificationCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendPaymentNotification";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Send a payment notification.");
        subparser.addArgument("recipient").help("Specify the recipient's phone number.");
        subparser.addArgument("--receipt").required(true).help("The base64 encoded receipt blob.");
        subparser.addArgument("--note").help("Specify a note for the payment notification.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var recipientString = ns.getString("recipient");
        final var recipientIdentifier = CommandUtil.getSingleRecipientIdentifier(recipientString, m.getSelfNumber());

        final var receiptString = ns.getString("receipt");
        final var receipt = Base64.getDecoder().decode(receiptString);
        final var note = ns.getString("note");

        try {
            final var results = m.sendPaymentNotificationMessage(receipt, note, recipientIdentifier);
            outputResult(outputWriter, results);
        } catch (IOException e) {
            throw new UnexpectedErrorException("Failed to send message: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }
}
