package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadProfileJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProfileJob.class);
    private final RecipientAddress address;
    private final boolean resolveIdentityKeyConflict;

    public DownloadProfileJob(RecipientAddress address) {
        this(address, false);
    }

    public DownloadProfileJob(RecipientAddress address, boolean resolveIdentityKeyConflict) {
        this.address = address;
        this.resolveIdentityKeyConflict = resolveIdentityKeyConflict;
    }

    @Override
    public void run(Context context) {
        logger.trace("Refreshing profile for {}", address);
        final var account = context.getAccount();
        final var recipientId = account.getRecipientStore().resolveRecipient(address);
        final var refreshed = context.getProfileHelper().refreshRecipientProfile(recipientId);
        if (refreshed && resolveIdentityKeyConflict) {
            context.getJobExecutor().enqueueJob(new SyncStorageJob());
        }
    }
}
