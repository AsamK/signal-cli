package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;

public class SendSyncBlockedListAction implements HandleAction {

    private static final SendSyncBlockedListAction INSTANCE = new SendSyncBlockedListAction();

    private SendSyncBlockedListAction() {
    }

    public static SendSyncBlockedListAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendBlockedList();
    }
}
