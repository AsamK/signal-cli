package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.json.JsonAttachmentData;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class AttachmentCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "attachment";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--id")
                .help("The ID of the attachment file.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {

        final var id = ns.getString("id");

        try(InputStream attachment = m.retrieveAttachment(id)) {

            final var bytes = attachment.readAllBytes();
            final var base64 = Base64.getEncoder().encodeToString(bytes);

            if (outputWriter instanceof PlainTextWriter writer) {
                writer.println(base64);
            }
            else if (outputWriter instanceof JsonWriter writer) {
                writer.write(new JsonAttachmentData(base64));
            }
        } catch (IOException ex) {
            throw new UnexpectedErrorException("An error occurred reading attachment: " + id, ex);
        }
    }
}
