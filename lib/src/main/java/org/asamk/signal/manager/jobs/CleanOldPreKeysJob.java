package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanOldPreKeysJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(CleanOldPreKeysJob.class);

    @Override
    public void run(Context context) {
        logger.trace("Cleaning old prekeys");
        context.getPreKeyHelper().cleanOldPreKeys();
    }
}
