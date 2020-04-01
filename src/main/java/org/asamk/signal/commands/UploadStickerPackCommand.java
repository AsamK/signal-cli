package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.StickerPackInvalidException;
import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class UploadStickerPackCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("path")
                .help("The path of the manifest.json or a zip file containing the sticker pack you wish to upload.");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        try {
            String path = ns.getString("path");
            String url = m.uploadStickerPack(path);
            System.out.println(url);
            return 0;
        } catch (IOException e) {
            System.err.println("Upload error: " + e.getMessage());
            return 3;
        } catch (StickerPackInvalidException e) {
            System.err.println("Invalid sticker pack: " + e.getMessage());
            return 3;
        }
    }
}
