package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.api.Configuration;

import java.io.IOException;
import java.util.Optional;

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
        final var readReceipts = ns.getBoolean("read-receipts");
        final var unidentifiedDeliveryIndicators = ns.getBoolean("unidentified-delivery-indicators");
        final var typingIndicators = ns.getBoolean("typing-indicators");
        final var linkPreviews = ns.getBoolean("link-previews");
        try {
            m.updateConfiguration(new Configuration(Optional.ofNullable(readReceipts),
                    Optional.ofNullable(unidentifiedDeliveryIndicators),
                    Optional.ofNullable(typingIndicators),
                    Optional.ofNullable(linkPreviews)));
        } catch (IOException e) {
            throw new IOErrorException("UpdateAccount error: " + e.getMessage(), e);
        } catch (NotMasterDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        }
    }
}
