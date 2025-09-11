package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.json.JsonAttachmentData;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.UnregisteredRecipientException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class GetAvatarCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "getAvatar";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Retrieve the avatar of a contact, contact's profile or group base64 encoded.");
        var mut = subparser.addMutuallyExclusiveGroup().required(true);
        mut.addArgument("-c", "--contact").help("Get a contact avatar");
        mut.addArgument("-p", "--profile").help("Get a profile avatar");
        mut.addArgument("-g", "--group-id").help("Get a group avatar");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var contactRecipient = ns.getString("contact");
        final var profileRecipient = ns.getString("profile");
        final var groupId = ns.getString("group-id");

        final InputStream data;
        try {
            if (contactRecipient != null) {
                data = m.retrieveContactAvatar(CommandUtil.getSingleRecipientIdentifier(contactRecipient,
                        m.getSelfNumber()));
            } else if (profileRecipient != null) {
                data = m.retrieveProfileAvatar(CommandUtil.getSingleRecipientIdentifier(profileRecipient,
                        m.getSelfNumber()));
            } else {
                data = m.retrieveGroupAvatar(CommandUtil.getGroupId(groupId));
            }
        } catch (FileNotFoundException ex) {
            throw new UserErrorException("Could not find avatar", ex);
        } catch (IOException ex) {
            throw new UnexpectedErrorException("An error occurred reading avatar", ex);
        } catch (UnregisteredRecipientException e) {
            throw new UserErrorException("The user " + e.getSender().getIdentifier() + " is not registered.");
        }

        try (data) {
            final var bytes = data.readAllBytes();
            final var base64 = Base64.getEncoder().encodeToString(bytes);
            switch (outputWriter) {
                case PlainTextWriter writer -> writer.println(base64);
                case JsonWriter writer -> writer.write(new JsonAttachmentData(base64));
            }
        } catch (IOException ex) {
            throw new UnexpectedErrorException("An error occurred reading avatar", ex);
        }
    }
}
