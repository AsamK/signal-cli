package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.StickerPack;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.Hex;

import java.util.List;

public class ListStickerPacksCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "listStickerPacks";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of known sticker packs.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager c, final OutputWriter outputWriter
    ) throws CommandException {
        final var stickerPacks = c.getStickerPacks();
        if (outputWriter instanceof JsonWriter jsonWriter) {
            final var jsonStickerPacks = stickerPacks.stream().map(JsonStickerPack::new).toList();
            jsonWriter.write(jsonStickerPacks);
        } else if (outputWriter instanceof PlainTextWriter plainTextWriter) {
            for (final var sticker : stickerPacks) {
                plainTextWriter.println("Pack {}: “{}” by “{}” has {} stickers. {}",
                        Hex.toStringCondensed(sticker.packId().serialize()),
                        sticker.title(),
                        sticker.author(),
                        sticker.stickers().size(),
                        sticker.url().getUrl());
            }
        }
    }

    private record JsonStickerPack(
            String packId,
            String url,
            boolean installed,
            String title,
            String author,
            JsonSticker cover,
            List<JsonSticker> stickers
    ) {

        JsonStickerPack(StickerPack stickerPack) {
            this(Hex.toStringCondensed(stickerPack.packId().serialize()),
                    stickerPack.url().getUrl().toString(),
                    stickerPack.installed(),
                    stickerPack.title(),
                    stickerPack.author(),
                    stickerPack.cover().map(JsonSticker::new).orElse(null),
                    stickerPack.stickers().stream().map(JsonSticker::new).toList());
        }

        private record JsonSticker(int id, String emoji, String contentType) {

            JsonSticker(StickerPack.Sticker sticker) {
                this(sticker.id(), sticker.emoji(), sticker.contentType());
            }
        }
    }
}
