package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public class UpdateAccountAttributesAction implements HandleAction {

    private static final UpdateAccountAttributesAction INSTANCE = new UpdateAccountAttributesAction();

    private UpdateAccountAttributesAction() {
    }

    public static UpdateAccountAttributesAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccountHelper().updateAccountAttributes();
    }
}
