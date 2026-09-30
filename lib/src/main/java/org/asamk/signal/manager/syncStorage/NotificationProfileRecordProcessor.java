package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class NotificationProfileRecordProcessor extends DefaultStorageRecordProcessor<SignalNotificationProfileRecord> {

    private final SignalAccount account;
    private final Connection connection;

    public NotificationProfileRecordProcessor(final SignalAccount account, final Connection connection) {
        this.account = account;
        this.connection = connection;
    }

    @Override
    public int compare(final SignalNotificationProfileRecord lhs, final SignalNotificationProfileRecord rhs) {
        return lhs.getProto().id.equals(rhs.getProto().id) ? 0 : 1;
    }

    @Override
    protected boolean isInvalid(final SignalNotificationProfileRecord remote) {
        return remote.getProto().id.size() == 0;
    }

    @Override
    protected Optional<SignalNotificationProfileRecord> getMatching(final SignalNotificationProfileRecord remote) throws SQLException {
        final var profileId = remote.getProto().id.toByteArray();
        final var local = account.getNotificationProfileStore().getNotificationProfile(connection, profileId);

        if (local == null) {
            return Optional.empty();
        }

        final StorageId storageId;
        if (local.storageId() != null) {
            storageId = local.storageId();
        } else {
            storageId = StorageId.forNotificationProfile(KeyUtils.createRawStorageId());
            account.getNotificationProfileStore().updateStorageId(connection, profileId, storageId);
        }

        return Optional.of(new SignalNotificationProfileRecord(storageId, StorageSyncModels.localToRemoteRecord(local)));
    }

    @Override
    protected SignalNotificationProfileRecord merge(
            final SignalNotificationProfileRecord remoteRecord,
            final SignalNotificationProfileRecord localRecord
    ) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();

        // Profiles are only ever edited by the official clients, so remote wins.
        // The one exception is an older local deletion, which must not be resurrected.
        if (StickerPackRecordProcessor.shouldKeepLocalDeletion(remote.deletedAtTimestampMs,
                local.deletedAtTimestampMs)) {
            return localRecord;
        }

        return remoteRecord;
    }

    @Override
    protected void insertLocal(final SignalNotificationProfileRecord record) throws SQLException {
        account.getNotificationProfileStore().upsertFromStorageSync(connection, record);
    }

    @Override
    protected void updateLocal(final StorageRecordUpdate<SignalNotificationProfileRecord> update) throws SQLException {
        account.getNotificationProfileStore().upsertFromStorageSync(connection, update.newRecord());
    }
}
