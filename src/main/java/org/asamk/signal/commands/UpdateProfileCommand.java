package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

import java.io.File;
import java.io.IOException;

public class UpdateProfileCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        final MutuallyExclusiveGroup avatarOptions = subparser.addMutuallyExclusiveGroup();
        avatarOptions.addArgument("--avatar")
                .help("Path to new profile avatar");
        avatarOptions.addArgument("--remove-avatar")
                .action(Arguments.storeTrue());

        subparser.addArgument("--name")
                .help("New profile name");

        subparser.help("Set a name and/or avatar image for the user profile");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        String name = ns.getString("name");

        if (name != null) {
            try {
                m.setProfileName(name);
            } catch (IOException e) {
                System.err.println("UpdateAccount error: " + e.getMessage());
                return 3;
            }
        }

        String avatarPath = ns.getString("avatar");

        if (avatarPath != null) {
            File avatarFile = new File(avatarPath);

            try {
                m.setProfileAvatar(avatarFile);
            } catch (IOException e) {
                System.err.println("UpdateAccount error: " + e.getMessage());
                return 3;
            }
        }

        boolean removeAvatar = ns.getBoolean("remove_avatar");

        if (removeAvatar) {
            try {
                m.removeProfileAvatar();
            } catch (IOException e) {
                System.err.println("UpdateAccount error: " + e.getMessage());
                return 3;
            }
        }

        return 0;
    }
}
