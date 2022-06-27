package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.jobs.SyncStorageJob;

public class SyncStorageDataAction implements HandleAction {

    private static final SyncStorageDataAction INSTANCE = new SyncStorageDataAction();

    private SyncStorageDataAction() {
    }

    public static SyncStorageDataAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getJobExecutor().enqueueJob(new SyncStorageJob());
    }
}
