package org.asamk.signal.manager.syncStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * An implementation of {@link StorageRecordProcessor} that solidifies a pattern and reduces
 * duplicate code in individual implementations.
 * <p>
 * Concerning the implementation of {@link #compare(Object, Object)}, it's purpose is to detect if
 * two items would map to the same logical entity (i.e. they would correspond to the same record in
 * our local store). We use it for a {@link TreeSet}, so mainly it's just important that the '0'
 * case is correct. Other cases are whatever, just make it something stable.
 */
abstract class DefaultStorageRecordProcessor<E extends SignalRecord<?>> implements StorageRecordProcessor<E>, Comparator<E> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultStorageRecordProcessor.class);
    private final Set<E> matchedRecords = new TreeSet<>(this);

    /**
     * One type of invalid remote data this handles is two records mapping to the same local data. We
     * have to trim this bad data out, because if we don't, we'll upload an ID set that only has one
     * of the IDs in it, but won't properly delete the dupes, which will then fail our validation
     * checks.
     * <p>
     * This is a bit tricky -- as we process records, IDs are written back to the local store, so we
     * can't easily be like "oh multiple records are mapping to the same local storage ID". And in
     * general we rely on SignalRecords to implement an equals() that includes the StorageId, so using
     * a regular set is out. Instead, we use a {@link TreeSet}, which allows us to define a custom
     * comparator for checking equality. Then we delegate to the subclass to tell us if two items are
     * the same based on their actual data (i.e. two contacts having the same UUID, or two groups
     * having the same MasterKey).
     */
    @Override
    public void process(E remote) throws SQLException {
        if (isInvalid(remote)) {
            debug(remote.getId(), remote, "Found invalid key! Ignoring it.");
            return;
        }

        final var local = getMatching(remote);

        if (local.isEmpty()) {
            debug(remote.getId(), remote, "No matching local record. Inserting.");
            insertLocal(remote);
            return;
        }

        if (matchedRecords.contains(local.get())) {
            debug(remote.getId(),
                    remote,
                    "Multiple remote records map to the same local record " + local.get() + "! Ignoring this one.");
            return;
        }

        matchedRecords.add(local.get());

        final var merged = merge(remote, local.get());
        if (!merged.equals(remote)) {
            debug(remote.getId(), remote, "[Remote Update] " + merged.describeDiff(remote));
        }

        if (!merged.equals(local.get())) {
            final var update = new StorageRecordUpdate<>(local.get(), merged);
            debug(remote.getId(), remote, "[Local Update] " + update);
            updateLocal(update);
        }
    }

    private void debug(StorageId i, E record, String message) {
        logger.debug("[{}][{}] {}", i, record.getClass().getSimpleName(), message);
    }

    /**
     * @return True if the record is invalid and should be removed from storage service, otherwise false.
     */
    protected abstract boolean isInvalid(E remote) throws SQLException;

    /**
     * Only records that pass the validity check (i.e. return false from {@link #isInvalid(SignalRecord)})
     * make it to here, so you can assume all records are valid.
     */
    protected abstract Optional<E> getMatching(E remote) throws SQLException;

    protected abstract E merge(E remote, E local);

    protected abstract void insertLocal(E record) throws SQLException;

    protected abstract void updateLocal(StorageRecordUpdate<E> update) throws SQLException;
}
