package org.asamk.signal.manager.syncStorage;

import org.whispersystems.signalservice.api.storage.SignalRecord;

/**
 * Represents a pair of records: one old, and one new. The new record should replace the old.
 */
record StorageRecordUpdate<E extends SignalRecord<?>>(E oldRecord, E newRecord) {

    @Override
    public String toString() {
        return newRecord.describeDiff(oldRecord);
    }
}
