package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class UploadStickerPackCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(UploadStickerPackCommand.class);

    @Override
    public String getName() {
        return "uploadStickerPack";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Upload a new sticker pack, consisting of a manifest file and the stickers images.");
        subparser.addArgument("path")
                .help("The path of the manifest.json or a zip file containing the sticker pack you wish to upload.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var writer = (PlainTextWriter) outputWriter;
        var path = new File(ns.getString("path"));

        try {
            var url = m.uploadStickerPack(path);
            writer.println("{}", url);
        } catch (IOException e) {
            throw new IOErrorException("Upload error (maybe image size too large):" + e.getMessage());
        } catch (StickerPackInvalidException e) {
            throw new UserErrorException("Invalid sticker pack: " + e.getMessage());
        }
    }
}
