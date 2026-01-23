package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public class RetrieveDeviceNameAction implements HandleAction {

    private static final RetrieveDeviceNameAction INSTANCE = new RetrieveDeviceNameAction();

    public static RetrieveDeviceNameAction create() {
        return INSTANCE;
    }

    private RetrieveDeviceNameAction() {
    }

    @Override
    public void execute(Context context) throws Throwable {
        context.getAccountHelper().refreshDeviceName();
    }
}
