package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;

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
            final var id = GroupId.unknownVersion(remote.getGroupId());
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
        final var id = GroupId.v1(remote.getGroupId());
        final var group = account.getGroupStore().getGroup(connection, id);

        if (group == null) {
            return Optional.empty();
        }

        final var storageId = account.getGroupStore().getGroupStorageId(connection, id);
        return Optional.of(StorageSyncModels.localToRemoteRecord(group, storageId.getRaw()).getGroupV1().get());
    }

    @Override
    protected SignalGroupV1Record merge(SignalGroupV1Record remote, SignalGroupV1Record local) {
        final var unknownFields = remote.serializeUnknownFields();
        final var blocked = remote.isBlocked();
        final var profileSharing = remote.isProfileSharingEnabled();
        final var archived = remote.isArchived();
        final var forcedUnread = remote.isForcedUnread();
        final var muteUntil = remote.getMuteUntil();

        final var mergedBuilder = new SignalGroupV1Record.Builder(remote.getId().getRaw(),
                remote.getGroupId(),
                unknownFields).setBlocked(blocked)
                .setProfileSharingEnabled(profileSharing)
                .setForcedUnread(forcedUnread)
                .setMuteUntil(muteUntil)
                .setArchived(archived);

        final var merged = mergedBuilder.build();

        final var matchesRemote = doProtosMatch(merged, remote);
        if (matchesRemote) {
            return remote;
        }

        final var matchesLocal = doProtosMatch(merged, local);
        if (matchesLocal) {
            return local;
        }

        return mergedBuilder.setId(KeyUtils.createRawStorageId()).build();
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
        final var groupIdV1 = GroupId.v1(groupV1Record.getGroupId());

        final var group = account.getGroupStore().getOrCreateGroupV1(connection, groupIdV1);
        if (group != null) {
            group.setBlocked(groupV1Record.isBlocked());
            account.getGroupStore().updateGroup(connection, group);
            account.getGroupStore()
                    .storeStorageRecord(connection,
                            group.getGroupId(),
                            groupV1Record.getId(),
                            groupV1Record.toProto().encode());
        }
    }

    @Override
    public int compare(SignalGroupV1Record lhs, SignalGroupV1Record rhs) {
        if (Arrays.equals(lhs.getGroupId(), rhs.getGroupId())) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean doProtosMatch(SignalGroupV1Record merged, SignalGroupV1Record other) {
        return Arrays.equals(merged.toProto().encode(), other.toProto().encode());
    }
}
