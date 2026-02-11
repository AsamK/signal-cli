package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;

public class StartCallCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "startCall";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Start an outgoing voice call.");
        subparser.addArgument("recipient").help("Specify the recipient's phone number or UUID.").nargs(1);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var recipientStrings = ns.<String>getList("recipient");
        if (recipientStrings == null || recipientStrings.isEmpty()) {
            throw new UserErrorException("No recipient given");
        }

        final var recipient = CommandUtil.getSingleRecipientIdentifier(recipientStrings.getFirst(), m.getSelfNumber());

        try {
            var callInfo = m.startCall(recipient);
            switch (outputWriter) {
                case PlainTextWriter writer -> {
                    writer.println("Call started:");
                    writer.println("  Call ID: {}", callInfo.callId());
                    writer.println("  State: {}", callInfo.state());
                    writer.println("  Input device: {}", callInfo.inputDeviceName());
                    writer.println("  Output device: {}", callInfo.outputDeviceName());
                }
                case JsonWriter writer -> writer.write(new JsonCallInfo(callInfo.callId(),
                        callInfo.state().name(),
                        callInfo.inputDeviceName(),
                        callInfo.outputDeviceName(),
                        "opus",
                        48000,
                        1,
                        20));
            }
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("Recipient not registered: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IOErrorException("Failed to start call: " + e.getMessage(), e);
        }
    }

    private record JsonCallInfo(
            long callId,
            String state,
            String inputDeviceName,
            String outputDeviceName,
            String codec,
            int sampleRate,
            int channels,
            int ptimeMs
    ) {}
}
