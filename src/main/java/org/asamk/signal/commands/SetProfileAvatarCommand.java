package org.asamk.signal.commands;

import java.io.IOException;
import java.io.File;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;

public class SetProfileAvatarCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("avatar")
                .help("Path to new profile avatar");
        subparser.help("Set the avatar for this profile");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        String avatarPath = ns.getString("avatar");
        File avatarFile = new File(avatarPath);

        try {
            m.setProfileAvatar(avatarFile);
        } catch (IOException e) {
            System.err.println("UpdateAccount error: " + e.getMessage());
            return 3;
        }

        return 0;
    }

}
