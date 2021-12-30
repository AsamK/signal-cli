package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public class SendSyncContactsAction implements HandleAction {

    private static final SendSyncContactsAction INSTANCE = new SendSyncContactsAction();

    private SendSyncContactsAction() {
    }

    public static SendSyncContactsAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendContacts();
    }
}
