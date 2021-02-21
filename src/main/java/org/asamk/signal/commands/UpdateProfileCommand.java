package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;

public class UpdateProfileCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--name").help("New profile name");
        subparser.addArgument("--about").help("New profile about text");
        subparser.addArgument("--about-emoji").help("New profile about emoji");

        final var avatarOptions = subparser.addMutuallyExclusiveGroup();
        avatarOptions.addArgument("--avatar").help("Path to new profile avatar");
        avatarOptions.addArgument("--remove-avatar").action(Arguments.storeTrue());

        subparser.help("Set a name, about and avatar image for the user profile");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        var name = ns.getString("name");
        var about = ns.getString("about");
        var aboutEmoji = ns.getString("about_emoji");
        var avatarPath = ns.getString("avatar");
        boolean removeAvatar = ns.getBoolean("remove_avatar");

        try {
            Optional<File> avatarFile = removeAvatar
                    ? Optional.absent()
                    : avatarPath == null ? null : Optional.of(new File(avatarPath));
            m.setProfile(name, about, aboutEmoji, avatarFile);
        } catch (IOException e) {
            System.err.println("Update profile error: " + e.getMessage());
            return 3;
        }

        return 0;
    }
}
