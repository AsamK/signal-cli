package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Handles merging remote storage updates into local group v1 state.
 */
public final class GroupV1RecordProcessor extends DefaultStorageRecordProcessor<SignalGroupV1Record> {

    private static final Logger logger = LoggerFactory.getLogger(GroupV1RecordProcessor.class);
    private final SignalAccount account;
    private final Connection connection;

    public GroupV1RecordProcessor(SignalAccount account, Connection connection) {
        this.account = account;
        this.connection = connection;
    }

    /**
     * We want to catch:
     * - Invalid group IDs
     * - GV1 IDs that map to GV2 IDs, meaning we've already migrated them.
     */
    @Override
    protected boolean isInvalid(SignalGroupV1Record remote) throws SQLException {
        try {
            final var id = GroupId.unknownVersion(remote.getProto().id.toByteArray());
            if (!(id instanceof GroupIdV1)) {
                return true;
            }
            final var group = account.getGroupStore().getGroup(connection, id);

            if (group instanceof GroupInfoV2) {
                logger.debug("We already have an upgraded V2 group for this V1 group -- marking as invalid.");
                return true;
            } else {
                return false;
            }
        } catch (AssertionError e) {
            logger.debug("Bad Group ID -- marking as invalid.");
            return true;
        }
    }

    @Override
    protected Optional<SignalGroupV1Record> getMatching(SignalGroupV1Record remote) throws SQLException {
        final var id = GroupId.v1(remote.getProto().id.toByteArray());
        final var group = account.getGroupStore().getGroup(connection, id);

        if (group == null) {
            return Optional.empty();
        }

        final var storageId = account.getGroupStore().getGroupStorageId(connection, id);
        return Optional.of(new SignalGroupV1Record(storageId, StorageSyncModels.localToRemoteRecord(group)));
    }

    @Override
    protected SignalGroupV1Record merge(SignalGroupV1Record remoteRecord, SignalGroupV1Record localRecord) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();

        final var mergedBuilder = SignalGroupV1Record.Companion.newBuilder(remote.unknownFields().toByteArray())
                .id(remote.id)
                .blocked(remote.blocked)
                .whitelisted(remote.whitelisted)
                .markedUnread(remote.markedUnread)
                .mutedUntilTimestamp(remote.mutedUntilTimestamp)
                .archived(remote.archived);

        final var merged = mergedBuilder.build();

        final var matchesRemote = doProtosMatch(merged, remote);
        if (matchesRemote) {
            return remoteRecord;
        }

        final var matchesLocal = doProtosMatch(merged, local);
        if (matchesLocal) {
            return localRecord;
        }

        return new SignalGroupV1Record(StorageId.forGroupV1(KeyUtils.createRawStorageId()), mergedBuilder.build());
    }

    @Override
    protected void insertLocal(SignalGroupV1Record record) throws SQLException {
        // TODO send group info request (after server message queue is empty)
        // context.getGroupHelper().sendGroupInfoRequest(groupIdV1, account.getSelfRecipientId());
        StorageRecordUpdate<SignalGroupV1Record> update = new StorageRecordUpdate<>(null, record);
        updateLocal(update);
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalGroupV1Record> update) throws SQLException {
        final var groupV1Record = update.newRecord();
        final var groupV1Proto = groupV1Record.getProto();
        final var groupIdV1 = GroupId.v1(groupV1Proto.id.toByteArray());

        final var group = account.getGroupStore().getOrCreateGroupV1(connection, groupIdV1);
        if (group != null) {
            group.setBlocked(groupV1Proto.blocked);
            account.getGroupStore().updateGroup(connection, group);
            account.getGroupStore()
                    .storeStorageRecord(connection, group.getGroupId(), groupV1Record.getId(), groupV1Proto.encode());
        }
    }

    @Override
    public int compare(SignalGroupV1Record lhs, SignalGroupV1Record rhs) {
        if (lhs.getProto().id.equals(rhs.getProto().id)) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean doProtosMatch(GroupV1Record merged, GroupV1Record other) {
        return Arrays.equals(merged.encode(), other.encode());
    }
}
