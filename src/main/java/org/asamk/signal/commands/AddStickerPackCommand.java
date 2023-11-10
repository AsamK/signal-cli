package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.StickerPackUrl;
import org.asamk.signal.output.OutputWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class AddStickerPackCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(AddStickerPackCommand.class);

    @Override
    public String getName() {
        return "addStickerPack";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Install a sticker pack for this account.");
        subparser.addArgument("--uri")
                .required(true)
                .nargs("+")
                .help("Specify the uri of the sticker pack. (e.g. https://signal.art/addstickers/#pack_id=XXX&pack_key=XXX)");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var uris = ns.<String>getList("uri");
        for (final var uri : uris) {
            final URI stickerUri;
            try {
                stickerUri = new URI(uri);
            } catch (URISyntaxException e) {
                throw new UserErrorException("Sticker pack uri has invalid format: " + e.getMessage());
            }

            try {
                var stickerPackUrl = StickerPackUrl.fromUri(stickerUri);
                m.installStickerPack(stickerPackUrl);
            } catch (IOException e) {
                logger.error("Install sticker pack failed: {}", e.getMessage());
                throw new IOErrorException("Install sticker pack failed", e);
            } catch (StickerPackUrl.InvalidStickerPackLinkException e) {
                logger.error("Invalid sticker pack link");
                throw new UserErrorException("Invalid sticker pack link", e);
            }
        }
    }
}
