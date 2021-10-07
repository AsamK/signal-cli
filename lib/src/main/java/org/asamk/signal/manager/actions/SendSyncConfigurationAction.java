package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;

public class SendSyncConfigurationAction implements HandleAction {

    private static final SendSyncConfigurationAction INSTANCE = new SendSyncConfigurationAction();

    private SendSyncConfigurationAction() {
    }

    public static SendSyncConfigurationAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendConfigurationMessage();
    }
}
