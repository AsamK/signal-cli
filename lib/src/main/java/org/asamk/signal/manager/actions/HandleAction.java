package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.jobs.Context;

public interface HandleAction {

    void execute(Context context) throws Throwable;
}
