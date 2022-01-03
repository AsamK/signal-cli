package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class UploadStickerPackCommand implements JsonRpcLocalCommand {

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
        var path = new File(ns.getString("path"));

        try {
            var url = m.uploadStickerPack(path);
            if (outputWriter instanceof PlainTextWriter writer) {
                writer.println("{}", url.getUrl());
            } else {
                final var writer = (JsonWriter) outputWriter;
                writer.write(Map.of("url", url.getUrl()));
            }
        } catch (IOException e) {
            throw new IOErrorException("Upload error (maybe image size too large):" + e.getMessage(), e);
        } catch (StickerPackInvalidException e) {
            throw new UserErrorException("Invalid sticker pack: " + e.getMessage());
        }
    }
}
