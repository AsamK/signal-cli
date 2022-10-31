package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.json.JsonAttachmentData;
import org.asamk.signal.manager.AttachmentPointer;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class AttachmentCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "attachment";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--file-id")
                .help("The ID of the attachment file.");
        subparser.addArgument("--file-name")
                .nargs("?")
                .help("The name of the file.");
        subparser.addArgument("--content-type")
                .nargs("?")
                .help("The content type of the file.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {

        final var id = ns.getString("file-id");
        final var fileName = ns.getString("file-name");
        final var contentType = ns.getString("content-type");

        final var file = m.getAttachmentFile(new AttachmentPointer(id, fileName, contentType));

        try {
            final var bytes = Files.readAllBytes(file.toPath());
            final var base64 = Base64.getEncoder().encodeToString(bytes);

            if (outputWriter instanceof PlainTextWriter writer) {
                writer.println(base64);
            }
            else if (outputWriter instanceof JsonWriter writer) {
                writer.write(new JsonAttachmentData(base64));
            }
        } catch (IOException ex) {
            throw new UnexpectedErrorException("An error occurred reading attachment file: " + file, ex);
        }
    }
}
