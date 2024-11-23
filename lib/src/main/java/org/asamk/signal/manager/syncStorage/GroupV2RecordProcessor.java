package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.jetbrains.annotations.NotNull;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

import okio.ByteString;

public final class GroupV2RecordProcessor extends DefaultStorageRecordProcessor<SignalGroupV2Record> {

    private static final Logger logger = LoggerFactory.getLogger(GroupV2RecordProcessor.class);
    private final SignalAccount account;
    private final Connection connection;

    public GroupV2RecordProcessor(SignalAccount account, Connection connection) {
        this.account = account;
        this.connection = connection;
    }

    @Override
    protected boolean isInvalid(SignalGroupV2Record remote) {
        return remote.getProto().masterKey.size() != GroupMasterKey.SIZE;
    }

    @Override
    protected Optional<SignalGroupV2Record> getMatching(SignalGroupV2Record remote) throws SQLException {
        final var id = GroupUtils.getGroupIdV2(getGroupMasterKeyOrThrow(remote.getProto().masterKey));
        final var group = account.getGroupStore().getGroup(connection, id);

        if (group == null) {
            return Optional.empty();
        }

        final var storageId = account.getGroupStore().getGroupStorageId(connection, id);
        return Optional.of(new SignalGroupV2Record(storageId, StorageSyncModels.localToRemoteRecord(group)));
    }

    @Override
    protected SignalGroupV2Record merge(SignalGroupV2Record remoteRecord, SignalGroupV2Record localRecord) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();

        final var mergedBuilder = SignalGroupV2Record.Companion.newBuilder(remote.unknownFields().toByteArray())
                .masterKey(remote.masterKey)
                .blocked(remote.blocked)
                .whitelisted(remote.whitelisted)
                .archived(remote.archived)
                .markedUnread(remote.markedUnread)
                .mutedUntilTimestamp(remote.mutedUntilTimestamp)
                .dontNotifyForMentionsIfMuted(remote.dontNotifyForMentionsIfMuted)
                .hideStory(remote.hideStory)
                .storySendMode(remote.storySendMode);
        final var merged = mergedBuilder.build();

        final var matchesRemote = doProtosMatch(merged, remote);
        if (matchesRemote) {
            return remoteRecord;
        }

        final var matchesLocal = doProtosMatch(merged, local);
        if (matchesLocal) {
            return localRecord;
        }

        return new SignalGroupV2Record(StorageId.forGroupV2(KeyUtils.createRawStorageId()), mergedBuilder.build());
    }

    @Override
    protected void insertLocal(SignalGroupV2Record record) throws SQLException {
        StorageRecordUpdate<SignalGroupV2Record> update = new StorageRecordUpdate<>(null, record);
        updateLocal(update);
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalGroupV2Record> update) throws SQLException {
        final var groupV2Record = update.newRecord();
        final var groupV2Proto = groupV2Record.getProto();
        final var groupMasterKey = getGroupMasterKeyOrThrow(groupV2Proto.masterKey);

        final var group = account.getGroupStore().getGroupOrPartialMigrate(connection, groupMasterKey);
        group.setBlocked(groupV2Proto.blocked);
        group.setProfileSharingEnabled(groupV2Proto.whitelisted);
        account.getGroupStore().updateGroup(connection, group);
        account.getGroupStore()
                .storeStorageRecord(connection, group.getGroupId(), groupV2Record.getId(), groupV2Proto.encode());
    }

    @NotNull
    private static GroupMasterKey getGroupMasterKeyOrThrow(final ByteString masterKey) {
        try {
            return new GroupMasterKey(masterKey.toByteArray());
        } catch (InvalidInputException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public int compare(SignalGroupV2Record lhs, SignalGroupV2Record rhs) {
        if (lhs.getProto().masterKey.equals(rhs.getProto().masterKey)) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean doProtosMatch(GroupV2Record merged, GroupV2Record other) {
        return Arrays.equals(merged.encode(), other.encode());
    }
}
