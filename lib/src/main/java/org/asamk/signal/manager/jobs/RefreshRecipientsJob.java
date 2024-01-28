package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefreshRecipientsJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(RefreshRecipientsJob.class);

    @Override
    public void run(Context context) {
        logger.trace("Full CDSI recipients refresh");
        try {
            context.getRecipientHelper().refreshUsers();
        } catch (Exception e) {
            logger.warn("Full CDSI recipients refresh failed, ignoring: {} ({})",
                    e.getMessage(),
                    e.getClass().getSimpleName());
            logger.debug("Full CDSI refresh failed", e);
        }
    }
}
