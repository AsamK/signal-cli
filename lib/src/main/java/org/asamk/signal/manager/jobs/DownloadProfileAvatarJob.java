package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadProfileAvatarJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProfileAvatarJob.class);
    private final String avatarPath;

    public DownloadProfileAvatarJob(final String avatarPath) {
        this.avatarPath = avatarPath;
    }

    @Override
    public void run(Context context) {
        logger.trace("Downloading profile avatar {}", avatarPath);
        final var account = context.getAccount();
        context.getProfileHelper()
                .downloadProfileAvatar(account.getSelfRecipientId(), avatarPath, account.getProfileKey());
    }
}
