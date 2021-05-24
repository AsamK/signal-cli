package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;

public class SendSyncKeysAction implements HandleAction {

    private static final SendSyncKeysAction INSTANCE = new SendSyncKeysAction();

    private SendSyncKeysAction() {
    }

    public static SendSyncKeysAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendKeysMessage();
    }
}
