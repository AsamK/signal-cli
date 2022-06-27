package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadProfileJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProfileJob.class);
    private final RecipientAddress address;

    public DownloadProfileJob(RecipientAddress address) {
        this.address = address;
    }

    @Override
    public void run(Context context) {
        logger.trace("Refreshing profile for {}", address);
        final var account = context.getAccount();
        final var recipientId = account.getRecipientStore().resolveRecipient(address);
        context.getProfileHelper().refreshRecipientProfile(recipientId);
    }
}
