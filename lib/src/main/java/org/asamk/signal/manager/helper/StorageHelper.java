package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.api.Profile;
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
import org.whispersystems.signalservice.api.storage.RecordIkm;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.storage.StorageRecordConvertersKt;
import org.whispersystems.signalservice.api.storage.StorageServiceRepository;
import org.whispersystems.signalservice.api.storage.StorageServiceRepository.ManifestIfDifferentVersionResult;
import org.whispersystems.signalservice.api.storage.StorageServiceRepository.WriteStorageRecordsResult;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.asamk.signal.manager.util.Utils.handleResponseException;

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
        var storageKey = account.getOrCreateStorageKey();
        if (storageKey == null) {
            if (!account.isPrimaryDevice()) {
                logger.debug("Storage key unknown, requesting from primary device.");
                context.getSyncHelper().requestSyncKeys();
            }
            return;
        }

        logger.trace("Reading manifest from remote storage");
        final var localManifestVersion = account.getStorageManifestVersion();
        final var localManifest = account.getStorageManifest().orElse(SignalStorageManifest.Companion.getEMPTY());
        final var storageServiceRepository = dependencies.getStorageServiceRepository();
        final var result = storageServiceRepository.getStorageManifestIfDifferentVersion(storageKey,
                localManifestVersion);

        var needsForcePush = false;
        final var remoteManifest = switch (result) {
            case ManifestIfDifferentVersionResult.DifferentVersion diff -> {
                final var manifest = diff.getManifest();
                storeManifestLocally(manifest);
                yield manifest;
            }
            case ManifestIfDifferentVersionResult.DecryptionError ignore -> {
                logger.warn("Manifest couldn't be decrypted.");
                if (account.isPrimaryDevice()) {
                    needsForcePush = true;
                } else {
                    context.getSyncHelper().requestSyncKeys();
                }
                yield null;
            }
            case ManifestIfDifferentVersionResult.SameVersion ignored -> localManifest;
            case ManifestIfDifferentVersionResult.NetworkError e -> throw e.getException();
            case ManifestIfDifferentVersionResult.StatusCodeError e -> throw e.getException();
            default -> throw new RuntimeException("Unhandled ManifestIfDifferentVersionResult type");
        };

        if (remoteManifest != null) {
            logger.trace("Manifest versions: local {}, remote {}", localManifestVersion, remoteManifest.version);

            if (remoteManifest.version > localManifestVersion) {
                logger.trace("Remote version was newer, reading records.");
                needsForcePush = readDataFromStorage(storageKey, localManifest, remoteManifest);
            } else if (remoteManifest.version < localManifest.version) {
                logger.debug("Remote storage manifest version was older. User might have switched accounts.");
            }
            logger.trace("Done reading data from remote storage");

            readRecordsWithPreviouslyUnknownTypes(storageKey, remoteManifest);
        }

        logger.trace("Adding missing storageIds to local data");
        account.getRecipientStore().setMissingStorageIds();
        account.getGroupStore().setMissingStorageIds();

        var needsMultiDeviceSync = false;

        if (account.needsStorageKeyMigration()) {
            logger.debug("Storage needs force push due to new account entropy pool");
            // Set new aep and reset previous master key and storage key
            account.setAccountEntropyPool(account.getOrCreateAccountEntropyPool());
            storageKey = account.getOrCreateStorageKey();
            context.getSyncHelper().sendKeysMessage();
            needsForcePush = true;
        } else if (remoteManifest == null) {
            if (account.isPrimaryDevice()) {
                needsForcePush = true;
            }
        } else if (remoteManifest.recordIkm == null && account.getSelfRecipientProfile()
                .getCapabilities()
                .contains(Profile.Capability.storageServiceEncryptionV2Capability)) {
            logger.debug("The SSRE2 capability is supported, but no recordIkm is set! Force pushing.");
            needsForcePush = true;
        } else {
            try {
                needsMultiDeviceSync = writeToStorage(storageKey, remoteManifest, needsForcePush);
            } catch (RetryLaterException e) {
                // TODO retry later
                return;
            }
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

    public void forcePushToStorage() throws IOException {
        if (!account.isPrimaryDevice()) {
            return;
        }

        final var storageKey = account.getOrCreateStorageKey();
        if (storageKey == null) {
            return;
        }

        try {
            forcePushToStorage(storageKey);
        } catch (RetryLaterException e) {
            // TODO retry later
        }
    }

    private boolean readDataFromStorage(
            final StorageKey storageKey,
            final SignalStorageManifest localManifest,
            final SignalStorageManifest remoteManifest
    ) throws IOException {
        var needsForcePush = false;
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);

            var idDifference = findIdDifference(remoteManifest.storageIds, localManifest.storageIds);

            if (idDifference.hasTypeMismatches() && account.isPrimaryDevice()) {
                logger.debug("Found type mismatches in the ID sets! Scheduling a force push after this sync completes.");
                needsForcePush = true;
            }

            logger.debug("Pre-Merge ID Difference :: {}", idDifference);

            if (!idDifference.isEmpty()) {
                final var remoteOnlyRecords = getSignalStorageRecords(storageKey,
                        remoteManifest,
                        idDifference.remoteOnlyIds());

                if (remoteOnlyRecords.size() != idDifference.remoteOnlyIds().size()) {
                    logger.debug(
                            "Could not find all remote-only records! Requested: {}, Found: {}. These stragglers should naturally get deleted during the sync.",
                            idDifference.remoteOnlyIds().size(),
                            remoteOnlyRecords.size());
                }

                if (!idDifference.localOnlyIds().isEmpty()) {
                    final var updated = account.getRecipientStore()
                            .removeStorageIdsFromLocalOnlyUnregisteredRecipients(connection,
                                    idDifference.localOnlyIds());

                    if (updated > 0) {
                        logger.warn(
                                "Found {} records that were deleted remotely but only marked unregistered locally. Removed those from local store.",
                                updated);
                    }
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

    private void readRecordsWithPreviouslyUnknownTypes(
            final StorageKey storageKey,
            final SignalStorageManifest remoteManifest
    ) throws IOException {
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);
            final var knownUnknownIds = account.getUnknownStorageIdStore()
                    .getUnknownStorageIds(connection, KNOWN_TYPES);

            if (!knownUnknownIds.isEmpty()) {
                logger.debug("We have {} unknown records that we can now process.", knownUnknownIds.size());

                final var remote = getSignalStorageRecords(storageKey, remoteManifest, knownUnknownIds);

                logger.debug("Found {} of the known-unknowns remotely.", remote.size());

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
            final StorageKey storageKey,
            final SignalStorageManifest remoteManifest,
            final boolean needsForcePush
    ) throws IOException, RetryLaterException {
        final WriteOperationResult remoteWriteOperation;
        try (final var connection = account.getAccountDatabase().getConnection()) {
            connection.setAutoCommit(false);

            var localStorageIds = getAllLocalStorageIds(connection);
            var idDifference = findIdDifference(remoteManifest.storageIds, localStorageIds);
            logger.debug("ID Difference :: {}", idDifference);

            final var unknownOnlyLocal = idDifference.localOnlyIds()
                    .stream()
                    .filter(id -> !KNOWN_TYPES.contains(id.getType()))
                    .toList();

            if (!unknownOnlyLocal.isEmpty()) {
                logger.debug("Storage ids with unknown type: {} to delete", unknownOnlyLocal.size());
                account.getUnknownStorageIdStore().deleteUnknownStorageIds(connection, unknownOnlyLocal);
                localStorageIds = getAllLocalStorageIds(connection);
                idDifference = findIdDifference(remoteManifest.storageIds, localStorageIds);
            }

            final var remoteDeletes = idDifference.remoteOnlyIds().stream().map(StorageId::getRaw).toList();
            final var remoteInserts = buildLocalStorageRecords(connection, idDifference.localOnlyIds());
            // TODO check if local storage record proto matches remote, then reset to remote storage_id

            remoteWriteOperation = new WriteOperationResult(new SignalStorageManifest(remoteManifest.version + 1,
                    account.getDeviceId(),
                    remoteManifest.recordIkm,
                    localStorageIds), remoteInserts, remoteDeletes);

            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }

        if (remoteWriteOperation.isEmpty()) {
            logger.debug("No remote writes needed. Still at version: {}", remoteManifest.version);
            return false;
        }

        logger.debug("We have something to write remotely.");
        logger.debug("WriteOperationResult :: {}", remoteWriteOperation);

        StorageSyncValidations.validate(remoteWriteOperation,
                remoteManifest,
                needsForcePush,
                account.getSelfRecipientAddress());

        final var result = dependencies.getStorageServiceRepository()
                .writeStorageRecords(storageKey,
                        remoteWriteOperation.manifest(),
                        remoteWriteOperation.inserts(),
                        remoteWriteOperation.deletes());
        switch (result) {
            case WriteStorageRecordsResult.ConflictError ignored -> {
                logger.debug("Hit a conflict when trying to resolve the conflict! Retrying.");
                throw new RetryLaterException();
            }
            case WriteStorageRecordsResult.NetworkError networkError -> throw networkError.getException();
            case WriteStorageRecordsResult.StatusCodeError statusCodeError -> throw statusCodeError.getException();
            case WriteStorageRecordsResult.Success ignored -> {
                logger.debug("Saved new manifest. Now at version: {}", remoteWriteOperation.manifest().version);
                storeManifestLocally(remoteWriteOperation.manifest());
                return true;
            }
            default -> throw new IllegalStateException("Unexpected value: " + result);
        }
    }

    private void forcePushToStorage(
            final StorageKey storageServiceKey
    ) throws IOException, RetryLaterException {
        logger.debug("Force pushing local state to remote storage");

        final var currentVersion = handleResponseException(dependencies.getStorageServiceRepository()
                .getManifestVersion());
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
                    final var accountRecord = StorageSyncModels.localToRemoteRecord(connection,
                            account.getConfigurationStore(),
                            recipient,
                            account.getUsernameLink());
                    newStorageRecords.add(new SignalStorageRecord(storageId,
                            new StorageRecord.Builder().account(accountRecord).build()));
                } else {
                    final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);
                    final var address = recipient.getAddress().getIdentifier();
                    final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, address);
                    final var record = StorageSyncModels.localToRemoteRecord(recipient, identity);
                    newStorageRecords.add(new SignalStorageRecord(storageId,
                            new StorageRecord.Builder().contact(record).build()));
                }
            }

            final var groupV1Ids = account.getGroupStore().getGroupV1Ids(connection);
            newGroupV1StorageIds = generateGroupV1StorageIds(groupV1Ids);
            for (final var groupId : groupV1Ids) {
                final var storageId = newGroupV1StorageIds.get(groupId);
                final var group = account.getGroupStore().getGroup(connection, groupId);
                final var record = StorageSyncModels.localToRemoteRecord(group);
                newStorageRecords.add(new SignalStorageRecord(storageId,
                        new StorageRecord.Builder().groupV1(record).build()));
            }

            final var groupV2Ids = account.getGroupStore().getGroupV2Ids(connection);
            newGroupV2StorageIds = generateGroupV2StorageIds(groupV2Ids);
            for (final var groupId : groupV2Ids) {
                final var storageId = newGroupV2StorageIds.get(groupId);
                final var group = account.getGroupStore().getGroup(connection, groupId);
                final var record = StorageSyncModels.localToRemoteRecord(group);
                newStorageRecords.add(new SignalStorageRecord(storageId,
                        new StorageRecord.Builder().groupV2(record).build()));
            }

            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sync remote storage", e);
        }
        final var newStorageIds = newStorageRecords.stream().map(SignalStorageRecord::getId).toList();

        final RecordIkm recordIkm;
        if (account.getSelfRecipientProfile()
                .getCapabilities()
                .contains(Profile.Capability.storageServiceEncryptionV2Capability)) {
            logger.debug("Generating and including a new recordIkm.");
            recordIkm = RecordIkm.Companion.generate();
        } else {
            logger.debug("SSRE2 not yet supported. Not including recordIkm.");
            recordIkm = null;
        }

        final var manifest = new SignalStorageManifest(newVersion, account.getDeviceId(), recordIkm, newStorageIds);

        StorageSyncValidations.validateForcePush(manifest, newStorageRecords, account.getSelfRecipientAddress());

        final WriteStorageRecordsResult result;
        if (newVersion > 1) {
            logger.trace("Force-pushing data. Inserting {} IDs.", newStorageRecords.size());
            result = dependencies.getStorageServiceRepository()
                    .resetAndWriteStorageRecords(storageServiceKey, manifest, newStorageRecords);
        } else {
            logger.trace("First version, normal push. Inserting {} IDs.", newStorageRecords.size());
            result = dependencies.getStorageServiceRepository()
                    .writeStorageRecords(storageServiceKey, manifest, newStorageRecords, Collections.emptyList());
        }

        switch (result) {
            case WriteStorageRecordsResult.ConflictError ignored -> {
                logger.debug("Hit a conflict. Trying again.");
                throw new RetryLaterException();
            }
            case WriteStorageRecordsResult.NetworkError networkError -> throw networkError.getException();
            case WriteStorageRecordsResult.StatusCodeError statusCodeError -> throw statusCodeError.getException();
            case WriteStorageRecordsResult.Success ignored -> {
                logger.debug("Force push succeeded. Updating local manifest version to: {}", manifest.version);
                storeManifestLocally(manifest);
            }
            default -> throw new IllegalStateException("Unexpected value: " + result);
        }

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
        account.setStorageManifestVersion(remoteManifest.version);
        account.setStorageManifest(remoteManifest);
    }

    private List<SignalStorageRecord> getSignalStorageRecords(
            final StorageKey storageKey,
            final SignalStorageManifest manifest,
            final List<StorageId> storageIds
    ) throws IOException {
        final var result = dependencies.getStorageServiceRepository()
                .readStorageRecords(storageKey, manifest.recordIkm, storageIds);
        return switch (result) {
            case StorageServiceRepository.StorageRecordResult.DecryptionError decryptionError -> {
                if (decryptionError.getException() instanceof InvalidKeyException) {
                    logger.warn("Failed to read storage records, ignoring.");
                    yield List.of();
                } else if (decryptionError.getException() instanceof IOException ioe) {
                    throw ioe;
                } else {
                    throw new IOException(decryptionError.getException());
                }
            }
            case StorageServiceRepository.StorageRecordResult.NetworkError networkError ->
                    throw networkError.getException();
            case StorageServiceRepository.StorageRecordResult.StatusCodeError statusCodeError ->
                    throw statusCodeError.getException();
            case StorageServiceRepository.StorageRecordResult.Success success -> success.getRecords();
            default -> throw new IllegalStateException("Unexpected value: " + result);
        };
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
            final Connection connection,
            final List<StorageId> storageIds
    ) throws SQLException {
        final var records = new ArrayList<SignalStorageRecord>(storageIds.size());
        for (final var storageId : storageIds) {
            final var record = buildLocalStorageRecord(connection, storageId);
            records.add(record);
        }
        return records;
    }

    private SignalStorageRecord buildLocalStorageRecord(
            Connection connection,
            StorageId storageId
    ) throws SQLException {
        return switch (ManifestRecord.Identifier.Type.fromValue(storageId.getType())) {
            case ManifestRecord.Identifier.Type.CONTACT -> {
                final var recipient = account.getRecipientStore().getRecipient(connection, storageId);
                final var address = recipient.getAddress().getIdentifier();
                final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, address);
                final var record = StorageSyncModels.localToRemoteRecord(recipient, identity);
                yield new SignalStorageRecord(storageId, new StorageRecord.Builder().contact(record).build());
            }
            case ManifestRecord.Identifier.Type.GROUPV1 -> {
                final var groupV1 = account.getGroupStore().getGroupV1(connection, storageId);
                final var record = StorageSyncModels.localToRemoteRecord(groupV1);
                yield new SignalStorageRecord(storageId, new StorageRecord.Builder().groupV1(record).build());
            }
            case ManifestRecord.Identifier.Type.GROUPV2 -> {
                final var groupV2 = account.getGroupStore().getGroupV2(connection, storageId);
                final var record = StorageSyncModels.localToRemoteRecord(groupV2);
                yield new SignalStorageRecord(storageId, new StorageRecord.Builder().groupV2(record).build());
            }
            case ManifestRecord.Identifier.Type.ACCOUNT -> {
                final var selfRecipient = account.getRecipientStore()
                        .getRecipient(connection, account.getSelfRecipientId());

                final var record = StorageSyncModels.localToRemoteRecord(connection,
                        account.getConfigurationStore(),
                        selfRecipient,
                        account.getUsernameLink());
                yield new SignalStorageRecord(storageId, new StorageRecord.Builder().account(record).build());
            }
            case null, default -> {
                throw new AssertionError("Got unknown local storage record type: " + storageId);
            }
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
            Collection<StorageId> remoteIds,
            Collection<StorageId> localIds
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

            if (remote.getType() != local.getType() && KNOWN_TYPES.contains(local.getType())) {
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
            final Connection connection,
            List<SignalStorageRecord> records
    ) throws SQLException {
        final var unknownRecords = new ArrayList<StorageId>();

        final var accountRecordProcessor = new AccountRecordProcessor(account, connection, context.getJobExecutor());
        final var contactRecordProcessor = new ContactRecordProcessor(account, connection, context.getJobExecutor());
        final var groupV1RecordProcessor = new GroupV1RecordProcessor(account, connection);
        final var groupV2RecordProcessor = new GroupV2RecordProcessor(account, connection);

        for (final var record : records) {
            if (record.getProto().account != null) {
                logger.debug("Reading record {} of type account", record.getId());
                accountRecordProcessor.process(StorageRecordConvertersKt.toSignalAccountRecord(record.getProto().account,
                        record.getId()));
            } else if (record.getProto().groupV1 != null) {
                logger.debug("Reading record {} of type groupV1", record.getId());
                groupV1RecordProcessor.process(StorageRecordConvertersKt.toSignalGroupV1Record(record.getProto().groupV1,
                        record.getId()));
            } else if (record.getProto().groupV2 != null) {
                logger.debug("Reading record {} of type groupV2", record.getId());
                groupV2RecordProcessor.process(StorageRecordConvertersKt.toSignalGroupV2Record(record.getProto().groupV2,
                        record.getId()));
            } else if (record.getProto().contact != null) {
                logger.debug("Reading record {} of type contact", record.getId());
                contactRecordProcessor.process(StorageRecordConvertersKt.toSignalContactRecord(record.getProto().contact,
                        record.getId()));
            } else {
                unknownRecords.add(record.getId());
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
