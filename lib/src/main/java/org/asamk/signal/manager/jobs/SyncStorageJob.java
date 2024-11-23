package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SyncStorageJob implements Job {

    private final boolean forcePush;

    private static final Logger logger = LoggerFactory.getLogger(SyncStorageJob.class);

    public SyncStorageJob() {
        this.forcePush = false;
    }

    public SyncStorageJob(final boolean forcePush) {
        this.forcePush = forcePush;
    }

    @Override
    public void run(Context context) {
        logger.trace("Running storage sync job");
        try {
            if (forcePush) {
                context.getStorageHelper().forcePushToStorage();
            } else {
                context.getStorageHelper().syncDataWithStorage();
            }
        } catch (IOException e) {
            logger.warn("Failed to sync storage data", e);
        }
    }
}
