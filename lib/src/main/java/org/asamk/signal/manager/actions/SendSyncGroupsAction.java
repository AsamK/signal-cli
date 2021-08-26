package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;

public class SendSyncGroupsAction implements HandleAction {

    private static final SendSyncGroupsAction INSTANCE = new SendSyncGroupsAction();

    private SendSyncGroupsAction() {
    }

    public static SendSyncGroupsAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendGroups();
    }
}
