package org.asamk.signal.manager.syncStorage;

import org.whispersystems.signalservice.api.storage.SignalRecord;

import java.sql.SQLException;

/**
 * Handles processing a remote record, which involves applying any local changes that need to be
 * made based on the remote records.
 */
interface StorageRecordProcessor<E extends SignalRecord<?>> {

    void process(E remoteRecord) throws SQLException;
}
