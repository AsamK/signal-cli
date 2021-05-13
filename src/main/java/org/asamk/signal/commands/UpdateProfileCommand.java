package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;

public class UpdateProfileCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--given-name", "--name").help("New profile (given) name");
        subparser.addArgument("--family-name").help("New profile family name (optional)");
        subparser.addArgument("--about").help("New profile about text");
        subparser.addArgument("--about-emoji").help("New profile about emoji");

        final var avatarOptions = subparser.addMutuallyExclusiveGroup();
        avatarOptions.addArgument("--avatar").help("Path to new profile avatar");
        avatarOptions.addArgument("--remove-avatar").action(Arguments.storeTrue());

        subparser.help("Set a name, about and avatar image for the user profile");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m) throws CommandException {
        var givenName = ns.getString("given-name");
        var familyName = ns.getString("family-name");
        var about = ns.getString("about");
        var aboutEmoji = ns.getString("about-emoji");
        var avatarPath = ns.getString("avatar");
        boolean removeAvatar = ns.getBoolean("remove-avatar");

        Optional<File> avatarFile = removeAvatar
                ? Optional.absent()
                : avatarPath == null ? null : Optional.of(new File(avatarPath));

        try {
            m.setProfile(givenName, familyName, about, aboutEmoji, avatarFile);
        } catch (IOException e) {
            throw new IOErrorException("Update profile error: " + e.getMessage());
        }
    }
}
