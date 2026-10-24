package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.StickerPackId;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.whispersystems.signalservice.api.storage.SignalInstalledStickerPackRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class StickerPackRecordProcessor extends DefaultStorageRecordProcessor<SignalInstalledStickerPackRecord> {

    private static final int PACK_ID_LENGTH = 16;
    private static final int PACK_KEY_LENGTH = 32;

    private final SignalAccount account;
    private final Connection connection;

    public StickerPackRecordProcessor(final SignalAccount account, final Connection connection) {
        this.account = account;
        this.connection = connection;
    }

    @Override
    public int compare(final SignalInstalledStickerPackRecord lhs, final SignalInstalledStickerPackRecord rhs) {
        return lhs.getProto().packId.equals(rhs.getProto().packId) ? 0 : 1;
    }

    @Override
    protected boolean isInvalid(final SignalInstalledStickerPackRecord remote) {
        return remote.getProto().packId.size() != PACK_ID_LENGTH || (
                remote.getProto().deletedAtTimestamp == 0 && remote.getProto().packKey.size() != PACK_KEY_LENGTH
        );
    }

    @Override
    protected Optional<SignalInstalledStickerPackRecord> getMatching(final SignalInstalledStickerPackRecord remote) throws SQLException {
        final var packId = StickerPackId.deserialize(remote.getProto().packId.toByteArray());
        final var local = account.getStickerStore().getStickerPack(connection, packId);

        if (local == null || (!local.isInstalled() && local.deletedTimestamp() == 0)) {
            return Optional.empty();
        }

        final StorageId storageId;
        if (local.storageId() != null) {
            storageId = local.storageId();
        } else {
            storageId = StorageId.forStickerPack(KeyUtils.createRawStorageId());
            account.getStickerStore().updateStorageId(connection, packId, storageId);
        }

        return Optional.of(new SignalInstalledStickerPackRecord(storageId, StorageSyncModels.localToRemoteRecord(local)));
    }

    @Override
    protected SignalInstalledStickerPackRecord merge(
            final SignalInstalledStickerPackRecord remoteRecord,
            final SignalInstalledStickerPackRecord localRecord
    ) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();

        if (shouldKeepLocalDeletion(remote.deletedAtTimestamp, local.deletedAtTimestamp)) {
            return localRecord;
        }

        return remoteRecord;
    }

    static boolean shouldKeepLocalDeletion(final long remoteDeletedAt, final long localDeletedAt) {
        return remoteDeletedAt > 0 && localDeletedAt > 0 && localDeletedAt < remoteDeletedAt;
    }

    @Override
    protected void insertLocal(final SignalInstalledStickerPackRecord record) throws SQLException {
        account.getStickerStore().upsertFromStorageSync(connection, record);
    }

    @Override
    protected void updateLocal(final StorageRecordUpdate<SignalInstalledStickerPackRecord> update) throws SQLException {
        account.getStickerStore().upsertFromStorageSync(connection, update.newRecord());
    }
}
