package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonAttachmentData;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.Hex;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class GetStickerCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "getSticker";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Retrieve the sticker of a sticker pack base64 encoded.");
        subparser.addArgument("--pack-id").required(true).help("The ID of the sticker pack.");
        subparser.addArgument("--sticker-id").type(int.class).required(true).help("The ID of the sticker.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {

        final var packId = StickerPackId.deserialize(Hex.toByteArray(ns.getString("pack-id")));
        final var stickerId = ns.getInt("sticker-id");

        try (InputStream data = m.retrieveSticker(packId, stickerId)) {
            final var bytes = data.readAllBytes();
            final var base64 = Base64.getEncoder().encodeToString(bytes);
            switch (outputWriter) {
                case PlainTextWriter writer -> writer.println(base64);
                case JsonWriter writer -> writer.write(new JsonAttachmentData(base64));
            }
        } catch (FileNotFoundException ex) {
            throw new UserErrorException("Could not find sticker with ID: " + stickerId + " in pack " + packId, ex);
        } catch (IOException ex) {
            throw new UnexpectedErrorException("An error occurred reading sticker with ID: "
                    + stickerId
                    + " in pack "
                    + packId, ex);
        }
    }
}
