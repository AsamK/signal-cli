package org.asamk.signal.manager.internal;

import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.concurrent.locks.ReentrantLock;

class ReentrantSignalSessionLock implements SignalSessionLock {

    private final ReentrantLock LEGACY_LOCK = new ReentrantLock();

    @Override
    public Lock acquire() {
        LEGACY_LOCK.lock();
        return LEGACY_LOCK::unlock;
    }
}
