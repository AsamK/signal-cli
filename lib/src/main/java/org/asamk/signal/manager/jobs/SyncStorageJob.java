package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SyncStorageJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(SyncStorageJob.class);

    @Override
    public void run(Context context) {
        logger.trace("Running storage sync job");
        try {
            context.getStorageHelper().syncDataWithStorage();
        } catch (IOException e) {
            logger.warn("Failed to sync storage data", e);
        }
    }
}
