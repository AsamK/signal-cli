package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.syncStorage.AccountRecordProcessor;
import org.asamk.signal.manager.syncStorage.ContactRecordProcessor;
import org.asamk.signal.manager.syncStorage.GroupV1RecordProcessor;
import org.asamk.signal.manager.syncStorage.GroupV2RecordProcessor;
import org.asamk.signal.manager.syncStorage.StorageSyncModels;
import org.asamk.signal.manager.syncStorage.StorageSyncValidations;
import org.asamk.signal.manager.syncStorage.WriteOperationResult;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.core.util.SetUtil;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class StorageHelper {

    private static final Logger logger = LoggerFactory.getLogger(StorageHelper.class);
    private static final List<Integer> KNOWN_TYPES = List.of(ManifestRecord.Identifier.Type.CONTACT.getValue(),
            ManifestRecord.Identifier.Type.GROUPV1.getValue(),
            ManifestRecord.Identifier.Type.GROUPV2.getValue(),
            ManifestRecord.Identifier.Type.ACCOUNT.getValue());

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    public StorageHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public void syncDataWithStorage() throws IOException {
        final var storageKey = account.getOrCreateStorageKey();
        if (storageKey == null) {
            if (!account.isPrimaryDevice()) {
                logger.debug("Storage key unknown, requesting from primary device.");
                context.getSyncHelper().requestSyncKeys();
            }
            return;
        }

        logger.trace("Reading manifest from remote storage");
        final var localManifestVersion = account.getStorageManifestVersion();
        final var localManifest = account.getStorageManifest().orElse(SignalStorageManifest.EMPTY);
        SignalStorageManifest remoteManifest;
        try {
            remoteManifest = dependencies.getAccountManager()
                    .getStorageManifestIfDifferentVersion(storageKey, localManifestVersion)
                    .orElse(localManifest);
        } catch (InvalidKeyException e) {
            logger.warn("Manifest couldn't be decrypted.");
            if (account.isPrimaryDevice()) {
                try {
                    forcePushToStorage(storageKey);
                } catch (RetryLaterException rle) {
                    // TODO retry later
                    return;
                }
            }
            return;
        }

        logger.trace("Manifest versions: local {}, remote {}", localManifestVersion, remoteManifest.getVersion());

        var needsForcePush = false;
        if (remoteManifest.getVersion() > localManifestVersion) {
            logger.trace("Remote version was newer, reading records.");
            needsForcePush = readDataFromStorage(storageKey, localManifest, remoteManifest);
        } else if (remoteManifest.getVersion() < localManifest.getVersion()) {
            logger.debug("Remote storage manifest version was older. User might have switched accounts.");
        }
        logger.trace("Done reading data from remote storage");

        if (localManifest != remoteManifest) {
            storeManifestLocally(remoteManifest);
        }

        readRecordsWithPreviouslyUnknownTypes(storageKey);

        logger.trace("Adding missing storageIds to local data");
        account.getRecipientStore().setMissingStorageIds();
        account.getGroupStore().setMissingStorageIds();

        var needsMultiDeviceSync = false;
        try {
            needsMultiDeviceSync = writeToStorage(storageKey, remoteManifest, needsForcePush);
        } catch (RetryLaterException e) {
            // TODO retry later
            return;
        }

        if (needsForcePush) {
            logger.debug("Doing a force push.");
            try {
                forcePushToStorage(storageKey);
                needsMultiDeviceSync = true;
            } catch (RetryLaterException e) {
                // TODO retry later
                return;
            }
        }

        if (needsMultiDeviceSync) {
            context.getSyncHelper().sendSyncFetchStorageMessage();
        }

        logger.debug("Done syncing data with remote storage");
    }

    private boolean readDataFromStorage(
            final StorageKey storageKey,
            final SignalStorageManifest localManifest,
            final SignalStorageManifest remoteManifest
    ) throws IOException {
        var needsForcePush = false;
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);

            var idDifference = findIdDifference(remoteManifest.getStorageIds(), localManifest.getStorageIds());

            if (idDifference.hasTypeMismatches() && account.isPrimaryDevice()) {
                logger.debug("Found type mismatches in the ID sets! Scheduling a force push after this sync completes.");
                needsForcePush = true;
            }

            logger.debug("Pre-Merge ID Difference :: " + idDifference);

            if (!idDifference.localOnlyIds().isEmpty()) {
                final var updated = account.getRecipientStore()
                        .removeStorageIdsFromLocalOnlyUnregisteredRecipients(connection, idDifference.localOnlyIds());

                if (updated > 0) {
                    logger.warn(
                            "Found {} records that were deleted remotely but only marked unregistered locally. Removed those from local store.",
                            updated);
                }
            }

            if (!idDifference.isEmpty()) {
                final var remoteOnlyRecords = getSignalStorageRecords(storageKey, idDifference.remoteOnlyIds());

                if (remoteOnlyRecords.size() != idDifference.remoteOnlyIds().size()) {
                    logger.debug("Could not find all remote-only records! Requested: "
                            + idDifference.remoteOnlyIds()
                            .size()
                            + ", Found: "
                            + remoteOnlyRecords.size()
                            + ". These stragglers should naturally get deleted during the sync.");
                }

                final var unknownInserts = processKnownRecords(connection, remoteOnlyRecords);
                final var unknownDeletes = idDifference.localOnlyIds()
                        .stream()
                        .filter(id -> !KNOWN_TYPES.contains(id.getType()))
                        .toList();

                logger.debug("Storage ids with unknown type: {} inserts, {} deletes",
                        unknownInserts.size(),
                        unknownDeletes.size());

                account.getUnknownStorageIdStore().addUnknownStorageIds(connection, unknownInserts);
                account.getUnknownStorageIdStore().deleteUnknownStorageIds(connection, unknownDeletes);
            } else {
                logger.debug("Remote version was newer, but there were no remote-only IDs.");
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }
        return needsForcePush;
    }

    private void readRecordsWithPreviouslyUnknownTypes(final StorageKey storageKey) throws IOException {
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);
            final var knownUnknownIds = account.getUnknownStorageIdStore()
                    .getUnknownStorageIds(connection, KNOWN_TYPES);

            if (!knownUnknownIds.isEmpty()) {
                logger.debug("We have " + knownUnknownIds.size() + " unknown records that we can now process.");

                final var remote = getSignalStorageRecords(storageKey, knownUnknownIds);

                logger.debug("Found " + remote.size() + " of the known-unknowns remotely.");

                processKnownRecords(connection, remote);
                account.getUnknownStorageIdStore()
                        .deleteUnknownStorageIds(connection, remote.stream().map(SignalStorageRecord::getId).toList());
            }
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }
    }

    private boolean writeToStorage(
            final StorageKey storageKey, final SignalStorageManifest remoteManifest, final boolean needsForcePush
    ) throws IOException, RetryLaterException {
        final WriteOperationResult remoteWriteOperation;
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);

            final var localStorageIds = getAllLocalStorageIds(connection);
            final var idDifference = findIdDifference(remoteManifest.getStorageIds(), localStorageIds);
            logger.debug("ID Difference :: " + idDifference);

            final var remoteDeletes = idDifference.remoteOnlyIds().stream().map(StorageId::getRaw).toList();
            final var remoteInserts = buildLocalStorageRecords(connection, idDifference.localOnlyIds());
            // TODO check if local storage record proto matches remote, then reset to remote storage_id

            remoteWriteOperation = new WriteOperationResult(new SignalStorageManifest(remoteManifest.getVersion() + 1,
                    account.getDeviceId(),
                    localStorageIds), remoteInserts, remoteDeletes);

            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }

        if (remoteWriteOperation.isEmpty()) {
            logger.debug("No remote writes needed. Still at version: " + remoteManifest.getVersion());
            return false;
        }

        logger.debug("We have something to write remotely.");
        logger.debug("WriteOperationResult :: " + remoteWriteOperation);

        StorageSyncValidations.validate(remoteWriteOperation,
                remoteManifest,
                needsForcePush,
                account.getSelfRecipientAddress());

        final Optional<SignalStorageManifest> conflict;
        try {
            conflict = dependencies.getAccountManager()
                    .writeStorageRecords(storageKey,
                            remoteWriteOperation.manifest(),
                            remoteWriteOperation.inserts(),
                            remoteWriteOperation.deletes());
        } catch (InvalidKeyException e) {
            logger.warn("Failed to decrypt conflicting storage manifest: {}", e.getMessage());
            throw new IOException(e);
        }

        if (conflict.isPresent()) {
            logger.debug("Hit a conflict when trying to resolve the conflict! Retrying.");
            throw new RetryLaterException();
        }

        logger.debug("Saved new manifest. Now at version: " + remoteWriteOperation.manifest().getVersion());
        storeManifestLocally(remoteWriteOperation.manifest());

        return true;
    }

    private void forcePushToStorage(
            final StorageKey storageServiceKey
    ) throws IOException, RetryLaterException {
        logger.debug("Force pushing local state to remote storage");

        final var currentVersion = dependencies.getAccountManager().getStorageManifestVersion();
        final var newVersion = currentVersion + 1;
        final var newStorageRecords = new ArrayList<SignalStorageRecord>();
        final Map<RecipientId, StorageId> newContactStorageIds;
        final Map<GroupIdV1, StorageId> newGroupV1StorageIds;
        final Map<GroupIdV2, StorageId> newGroupV2StorageIds;

        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);

            final var recipientIds = account.getRecipientStore().getRecipientIds(connection);
            newContactStorageIds = generateContactStorageIds(recipientIds);
            for (final var recipientId : recipientIds) {
                final var storageId = newContactStorageIds.get(recipientId);
                if (storageId.getType() == ManifestRecord.Identifier.Type.ACCOUNT.getValue()) {
                    final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);
                    final var accountRecord = StorageSyncModels.localToRemoteRecord(account.getConfigurationStore(),
                            recipient,
                            account.getUsernameLink(),
                            storageId.getRaw());
                    newStorageRecords.add(accountRecord);
                } else {
                    final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);
                    final var address = recipient.getAddress().getIdentifier();
                    final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, address);
                    final var record = StorageSyncModels.localToRemoteRecord(recipient, identity, storageId.getRaw());
                    newStorageRecords.add(record);
                }
            }

            final var groupV1Ids = account.getGroupStore().getGroupV1Ids(connection);
            newGroupV1StorageIds = generateGroupV1StorageIds(groupV1Ids);
            for (final var groupId : groupV1Ids) {
                final var storageId = newGroupV1StorageIds.get(groupId);
                final var group = account.getGroupStore().getGroup(connection, groupId);
                final var record = StorageSyncModels.localToRemoteRecord(group, storageId.getRaw());
                newStorageRecords.add(record);
            }

            final var groupV2Ids = account.getGroupStore().getGroupV2Ids(connection);
            newGroupV2StorageIds = generateGroupV2StorageIds(groupV2Ids);
            for (final var groupId : groupV2Ids) {
                final var storageId = newGroupV2StorageIds.get(groupId);
                final var group = account.getGroupStore().getGroup(connection, groupId);
                final var record = StorageSyncModels.localToRemoteRecord(group, storageId.getRaw());
                newStorageRecords.add(record);
            }

            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }
        final var newStorageIds = newStorageRecords.stream().map(SignalStorageRecord::getId).toList();

        final var manifest = new SignalStorageManifest(newVersion, account.getDeviceId(), newStorageIds);

        StorageSyncValidations.validateForcePush(manifest, newStorageRecords, account.getSelfRecipientAddress());

        final Optional<SignalStorageManifest> conflict;
        try {
            if (newVersion > 1) {
                logger.trace("Force-pushing data. Inserting {} IDs.", newStorageRecords.size());
                conflict = dependencies.getAccountManager()
                        .resetStorageRecords(storageServiceKey, manifest, newStorageRecords);
            } else {
                logger.trace("First version, normal push. Inserting {} IDs.", newStorageRecords.size());
                conflict = dependencies.getAccountManager()
                        .writeStorageRecords(storageServiceKey, manifest, newStorageRecords, Collections.emptyList());
            }
        } catch (InvalidKeyException e) {
            logger.debug("Hit an invalid key exception, which likely indicates a conflict.", e);
            throw new RetryLaterException();
        }

        if (conflict.isPresent()) {
            logger.debug("Hit a conflict. Trying again.");
            throw new RetryLaterException();
        }

        logger.debug("Force push succeeded. Updating local manifest version to: " + manifest.getVersion());
        storeManifestLocally(manifest);

        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);
            account.getRecipientStore().updateStorageIds(connection, newContactStorageIds);
            account.getGroupStore().updateStorageIds(connection, newGroupV1StorageIds, newGroupV2StorageIds);

            // delete all unknown storage ids
            account.getUnknownStorageIdStore().deleteAllUnknownStorageIds(connection);
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }
    }

    private Map<RecipientId, StorageId> generateContactStorageIds(List<RecipientId> recipientIds) {
        final var selfRecipientId = account.getSelfRecipientId();
        return recipientIds.stream().collect(Collectors.toMap(recipientId -> recipientId, recipientId -> {
            if (recipientId.equals(selfRecipientId)) {
                return StorageId.forAccount(KeyUtils.createRawStorageId());
            } else {
                return StorageId.forContact(KeyUtils.createRawStorageId());
            }
        }));
    }

    private Map<GroupIdV1, StorageId> generateGroupV1StorageIds(List<GroupIdV1> groupIds) {
        return groupIds.stream()
                .collect(Collectors.toMap(recipientId -> recipientId,
                        recipientId -> StorageId.forGroupV1(KeyUtils.createRawStorageId())));
    }

    private Map<GroupIdV2, StorageId> generateGroupV2StorageIds(List<GroupIdV2> groupIds) {
        return groupIds.stream()
                .collect(Collectors.toMap(recipientId -> recipientId,
                        recipientId -> StorageId.forGroupV2(KeyUtils.createRawStorageId())));
    }

    private void storeManifestLocally(
            final SignalStorageManifest remoteManifest
    ) {
        account.setStorageManifestVersion(remoteManifest.getVersion());
        account.setStorageManifest(remoteManifest);
    }

    private List<SignalStorageRecord> getSignalStorageRecords(
            final StorageKey storageKey, final List<StorageId> storageIds
    ) throws IOException {
        List<SignalStorageRecord> records;
        try {
            records = dependencies.getAccountManager().readStorageRecords(storageKey, storageIds);
        } catch (InvalidKeyException e) {
            logger.warn("Failed to read storage records, ignoring.");
            return List.of();
        }
        return records;
    }

    private List<StorageId> getAllLocalStorageIds(final Connection connection) throws SQLException {
        final var storageIds = new ArrayList<StorageId>();
        storageIds.addAll(account.getUnknownStorageIdStore().getUnknownStorageIds(connection));
        storageIds.addAll(account.getGroupStore().getStorageIds(connection));
        storageIds.addAll(account.getRecipientStore().getStorageIds(connection));
        storageIds.add(account.getRecipientStore().getSelfStorageId(connection));
        return storageIds;
    }

    private List<SignalStorageRecord> buildLocalStorageRecords(
            final Connection connection, final List<StorageId> storageIds
    ) throws SQLException {
        final var records = new ArrayList<SignalStorageRecord>();
        for (final var storageId : storageIds) {
            final var record = buildLocalStorageRecord(connection, storageId);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private SignalStorageRecord buildLocalStorageRecord(
            Connection connection, StorageId storageId
    ) throws SQLException {
        return switch (ManifestRecord.Identifier.Type.fromValue(storageId.getType())) {
            case ManifestRecord.Identifier.Type.CONTACT -> {
                final var recipient = account.getRecipientStore().getRecipient(connection, storageId);
                final var address = recipient.getAddress().getIdentifier();
                final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, address);
                yield StorageSyncModels.localToRemoteRecord(recipient, identity, storageId.getRaw());
            }
            case ManifestRecord.Identifier.Type.GROUPV1 -> {
                final var groupV1 = account.getGroupStore().getGroupV1(connection, storageId);
                yield StorageSyncModels.localToRemoteRecord(groupV1, storageId.getRaw());
            }
            case ManifestRecord.Identifier.Type.GROUPV2 -> {
                final var groupV2 = account.getGroupStore().getGroupV2(connection, storageId);
                yield StorageSyncModels.localToRemoteRecord(groupV2, storageId.getRaw());
            }
            case ManifestRecord.Identifier.Type.ACCOUNT -> {
                final var selfRecipient = account.getRecipientStore()
                        .getRecipient(connection, account.getSelfRecipientId());
                yield StorageSyncModels.localToRemoteRecord(account.getConfigurationStore(),
                        selfRecipient,
                        account.getUsernameLink(),
                        storageId.getRaw());
            }
            case null, default -> throw new AssertionError("Got unknown local storage record type: " + storageId);
        };
    }

    /**
     * Given a list of all the local and remote keys you know about, this will
     * return a result telling
     * you which keys are exclusively remote and which are exclusively local.
     *
     * @param remoteIds All remote keys available.
     * @param localIds  All local keys available.
     * @return An object describing which keys are exclusive to the remote data set
     * and which keys are
     * exclusive to the local data set.
     */
    private static IdDifferenceResult findIdDifference(
            Collection<StorageId> remoteIds, Collection<StorageId> localIds
    ) {
        final var base64Encoder = Base64.getEncoder();
        final var remoteByRawId = remoteIds.stream()
                .collect(Collectors.toMap(id -> base64Encoder.encodeToString(id.getRaw()), id -> id));
        final var localByRawId = localIds.stream()
                .collect(Collectors.toMap(id -> base64Encoder.encodeToString(id.getRaw()), id -> id));

        boolean hasTypeMismatch = remoteByRawId.size() != remoteIds.size() || localByRawId.size() != localIds.size();

        final var remoteOnlyRawIds = SetUtil.difference(remoteByRawId.keySet(), localByRawId.keySet());
        final var localOnlyRawIds = SetUtil.difference(localByRawId.keySet(), remoteByRawId.keySet());
        final var sharedRawIds = SetUtil.intersection(localByRawId.keySet(), remoteByRawId.keySet());

        for (String rawId : sharedRawIds) {
            final var remote = remoteByRawId.get(rawId);
            final var local = localByRawId.get(rawId);

            if (remote.getType() != local.getType() && local.getType() != 0) {
                remoteOnlyRawIds.remove(rawId);
                localOnlyRawIds.remove(rawId);
                hasTypeMismatch = true;
                logger.debug("Remote type {} did not match local type {} for {}!",
                        remote.getType(),
                        local.getType(),
                        rawId);
            }
        }

        final var remoteOnlyKeys = remoteOnlyRawIds.stream().map(remoteByRawId::get).toList();
        final var localOnlyKeys = localOnlyRawIds.stream().map(localByRawId::get).toList();

        return new IdDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch);
    }

    private List<StorageId> processKnownRecords(
            final Connection connection, List<SignalStorageRecord> records
    ) throws SQLException {
        final var unknownRecords = new ArrayList<StorageId>();

        final var accountRecordProcessor = new AccountRecordProcessor(account, connection, context.getJobExecutor());
        final var contactRecordProcessor = new ContactRecordProcessor(account, connection, context.getJobExecutor());
        final var groupV1RecordProcessor = new GroupV1RecordProcessor(account, connection);
        final var groupV2RecordProcessor = new GroupV2RecordProcessor(account, connection);

        for (final var record : records) {
            logger.debug("Reading record of type {}", record.getType());
            switch (ManifestRecord.Identifier.Type.fromValue(record.getType())) {
                case ACCOUNT -> accountRecordProcessor.process(record.getAccount().get());
                case GROUPV1 -> groupV1RecordProcessor.process(record.getGroupV1().get());
                case GROUPV2 -> groupV2RecordProcessor.process(record.getGroupV2().get());
                case CONTACT -> contactRecordProcessor.process(record.getContact().get());
                case null, default -> unknownRecords.add(record.getId());
            }
        }

        return unknownRecords;
    }

    /**
     * hasTypeMismatches is True if there exist some keys that have matching raw ID's but different types, otherwise false.
     */
    private record IdDifferenceResult(
            List<StorageId> remoteOnlyIds, List<StorageId> localOnlyIds, boolean hasTypeMismatches
    ) {

        public boolean isEmpty() {
            return remoteOnlyIds.isEmpty() && localOnlyIds.isEmpty();
        }
    }

    private static class RetryLaterException extends Throwable {}
}
