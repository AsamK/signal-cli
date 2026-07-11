package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

import static org.asamk.signal.util.SendMessageResultUtils.outputResult;

public class SendStoryCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "sendStory";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Post a story to your Story.");
        subparser.addArgument("-a", "--attachment")
                .required(true)
                .help("Specify the file path to the image or video to post as a story.");
        subparser.addArgument("--no-replies")
                .action(Arguments.storeTrue())
                .help("Disable replies on this story.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var attachment = ns.getString("attachment");
        if (attachment == null || attachment.isEmpty()) {
            throw new UserErrorException("An attachment is required for sending a story.");
        }

        final var noReplies = Boolean.TRUE.equals(ns.getBoolean("no-replies"));

        try {
            final var results = m.sendStory(attachment, !noReplies);
            outputResult(outputWriter, results);
        } catch (AttachmentInvalidException | IOException e) {
            throw new UnexpectedErrorException("Failed to send story: " + e.getMessage() + " (" + e.getClass()
                    .getSimpleName() + ")", e);
        }
    }
}
