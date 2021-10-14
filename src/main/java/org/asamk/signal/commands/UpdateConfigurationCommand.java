package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpdateConfigurationCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "updateConfiguration";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Update signal configs and sync them to linked devices.");
        subparser.addArgument("--read-receipts")
                .type(Boolean.class)
                .help("Indicates if Signal should send read receipts.");
        subparser.addArgument("--unidentified-delivery-indicators")
                .type(Boolean.class)
                .help("Indicates if Signal should show unidentified delivery indicators.");
        subparser.addArgument("--typing-indicators")
                .type(Boolean.class)
                .help("Indicates if Signal should send/show typing indicators.");
        subparser.addArgument("--link-previews")
                .type(Boolean.class)
                .help("Indicates if Signal should generate link previews.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var readReceipts = ns.getBoolean("read-receipts");
        var unidentifiedDeliveryIndicators = ns.getBoolean("unidentified-delivery-indicators");
        var typingIndicators = ns.getBoolean("typing-indicators");
        var linkPreviews = ns.getBoolean("link-previews");
        List<Boolean> configuration = new ArrayList<>(4);

        try {
            configuration = m.getConfiguration();
        } catch (IOException | NotMasterDeviceException e) {
            throw new CommandException(e.getMessage());
        }

        if (readReceipts == null) {
            try {
                readReceipts = configuration.get(0);
            } catch (NullPointerException e) {
                readReceipts = true;
            }
        }
        if (unidentifiedDeliveryIndicators == null) {
            try {
                unidentifiedDeliveryIndicators = configuration.get(1);
            } catch (NullPointerException e) {
                unidentifiedDeliveryIndicators = true;
            }
        }
        if (typingIndicators == null) {
            try {
                typingIndicators = configuration.get(2);
            } catch (NullPointerException e) {
                typingIndicators = true;
            }
        }
        if (linkPreviews == null) {
            try {
                linkPreviews = configuration.get(3);
            } catch (NullPointerException e) {
                linkPreviews = true;
            }
        }
        try {
            m.updateConfiguration(readReceipts, unidentifiedDeliveryIndicators, typingIndicators, linkPreviews);
            if (outputWriter instanceof JsonWriter) {
                final var writer = (JsonWriter) outputWriter;
                writer.write(Map.of("readReceipts", readReceipts, "unidentifiedDeliveryIndicators", unidentifiedDeliveryIndicators, "typingIndicators", typingIndicators, "linkPreviews", linkPreviews));
            } else {
                final var writer = (PlainTextWriter) outputWriter;
                writer.println("readReceipts=" + readReceipts
                        + "\nunidentifiedDeliveryIndicators=" + unidentifiedDeliveryIndicators
                        + "\ntypingIndicators=" + typingIndicators
                        + "\nlinkPreviews=" + linkPreviews
                        );
            }
        } catch (IOException e) {
            throw new IOErrorException("UpdateAccount error: " + e.getMessage(), e);
        } catch (NotMasterDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        }
    }
}
