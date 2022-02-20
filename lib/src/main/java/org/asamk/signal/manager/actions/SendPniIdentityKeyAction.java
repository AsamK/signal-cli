package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public class SendPniIdentityKeyAction implements HandleAction {

    private static final SendPniIdentityKeyAction INSTANCE = new SendPniIdentityKeyAction();

    private SendPniIdentityKeyAction() {
    }

    public static SendPniIdentityKeyAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getSyncHelper().sendPniIdentity();
    }
}
