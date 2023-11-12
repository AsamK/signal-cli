package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.helper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CheckWhoAmIJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(CheckWhoAmIJob.class);

    @Override
    public void run(Context context) {
        logger.trace("Checking whoAmI");
        try {
            context.getAccountHelper().checkWhoAmiI();
        } catch (IOException e) {
            logger.warn("Failed to check whoAmI", e);
        }
    }
}
