package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public class RefreshPreKeysAction implements HandleAction {

    private static final RefreshPreKeysAction INSTANCE = new RefreshPreKeysAction();

    private RefreshPreKeysAction() {
    }

    public static RefreshPreKeysAction create() {
        return INSTANCE;
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getPreKeyHelper().refreshPreKeysIfNecessary();
    }
}
