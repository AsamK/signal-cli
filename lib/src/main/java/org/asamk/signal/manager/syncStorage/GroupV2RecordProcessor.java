package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

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
        return remote.getMasterKeyBytes().length != GroupMasterKey.SIZE;
    }

    @Override
    protected Optional<SignalGroupV2Record> getMatching(SignalGroupV2Record remote) throws SQLException {
        final var id = GroupUtils.getGroupIdV2(remote.getMasterKeyOrThrow());
        final var group = account.getGroupStore().getGroup(connection, id);

        if (group == null) {
            return Optional.empty();
        }

        final var storageId = account.getGroupStore().getGroupStorageId(connection, id);
        return Optional.of(StorageSyncModels.localToRemoteRecord(group, storageId.getRaw()).getGroupV2().get());
    }

    @Override
    protected SignalGroupV2Record merge(SignalGroupV2Record remote, SignalGroupV2Record local) {
        final var unknownFields = remote.serializeUnknownFields();
        final var blocked = remote.isBlocked();
        final var profileSharing = remote.isProfileSharingEnabled();
        final var archived = remote.isArchived();
        final var forcedUnread = remote.isForcedUnread();
        final var muteUntil = remote.getMuteUntil();
        final var notifyForMentionsWhenMuted = remote.notifyForMentionsWhenMuted();
        final var hideStory = remote.shouldHideStory();
        final var storySendMode = remote.getStorySendMode();

        final var mergedBuilder = new SignalGroupV2Record.Builder(remote.getId().getRaw(),
                remote.getMasterKeyBytes(),
                unknownFields).setBlocked(blocked)
                .setProfileSharingEnabled(profileSharing)
                .setArchived(archived)
                .setForcedUnread(forcedUnread)
                .setMuteUntil(muteUntil)
                .setNotifyForMentionsWhenMuted(notifyForMentionsWhenMuted)
                .setHideStory(hideStory)
                .setStorySendMode(storySendMode);
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
    protected void insertLocal(SignalGroupV2Record record) throws SQLException {
        StorageRecordUpdate<SignalGroupV2Record> update = new StorageRecordUpdate<>(null, record);
        updateLocal(update);
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalGroupV2Record> update) throws SQLException {
        final var groupV2Record = update.newRecord();
        final var groupMasterKey = groupV2Record.getMasterKeyOrThrow();

        final var group = account.getGroupStore().getGroupOrPartialMigrate(connection, groupMasterKey);
        group.setBlocked(groupV2Record.isBlocked());
        group.setProfileSharingEnabled(groupV2Record.isProfileSharingEnabled());
        account.getGroupStore().updateGroup(connection, group);
        account.getGroupStore()
                .storeStorageRecord(connection,
                        group.getGroupId(),
                        groupV2Record.getId(),
                        groupV2Record.toProto().encode());
    }

    @Override
    public int compare(SignalGroupV2Record lhs, SignalGroupV2Record rhs) {
        if (Arrays.equals(lhs.getMasterKeyBytes(), rhs.getMasterKeyBytes())) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean doProtosMatch(SignalGroupV2Record merged, SignalGroupV2Record other) {
        return Arrays.equals(merged.toProto().encode(), other.toProto().encode());
    }
}
