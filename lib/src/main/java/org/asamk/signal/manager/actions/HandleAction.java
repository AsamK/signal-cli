package org.asamk.signal.manager.actions;

import org.asamk.signal.manager.helper.Context;

public interface HandleAction {

    void execute(Context context) throws Throwable;

    default void mergeOther(HandleAction action) {
    }
}
