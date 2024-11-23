package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.core.util.Base64;
import org.signal.core.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class StorageSyncValidations {

    private static final Logger logger = LoggerFactory.getLogger(StorageSyncValidations.class);

    private StorageSyncValidations() {
    }

    public static void validate(
            WriteOperationResult result,
            SignalStorageManifest previousManifest,
            boolean forcePushPending,
            RecipientAddress self
    ) {
        validateManifestAndInserts(result.manifest(), result.inserts(), self);

        if (!result.deletes().isEmpty()) {
            Set<String> allSetEncoded = result.manifest().storageIds.stream()
                    .map(StorageId::getRaw)
                    .map(Base64::encodeWithPadding)
                    .collect(Collectors.toSet());

            for (byte[] delete : result.deletes()) {
                String encoded = Base64.encodeWithPadding(delete);
                if (allSetEncoded.contains(encoded)) {
                    throw new DeletePresentInFullIdSetError();
                }
            }
        }

        if (previousManifest.version == 0) {
            logger.debug(
                    "Previous manifest is empty, not bothering with additional validations around the diffs between the two manifests.");
            return;
        }

        if (result.manifest().version != previousManifest.version + 1) {
            throw new IncorrectManifestVersionError();
        }

        if (forcePushPending) {
            logger.debug(
                    "Force push pending, not bothering with additional validations around the diffs between the two manifests.");
            return;
        }

        Set<ByteBuffer> previousIds = previousManifest.storageIds.stream()
                .map(id -> ByteBuffer.wrap(id.getRaw()))
                .collect(Collectors.toSet());
        Set<ByteBuffer> newIds = result.manifest().storageIds.stream()
                .map(id -> ByteBuffer.wrap(id.getRaw()))
                .collect(Collectors.toSet());

        Set<ByteBuffer> manifestInserts = SetUtil.difference(newIds, previousIds);
        Set<ByteBuffer> manifestDeletes = SetUtil.difference(previousIds, newIds);

        Set<ByteBuffer> declaredInserts = result.inserts()
                .stream()
                .map(r -> ByteBuffer.wrap(r.getId().getRaw()))
                .collect(Collectors.toSet());
        Set<ByteBuffer> declaredDeletes = result.deletes().stream().map(ByteBuffer::wrap).collect(Collectors.toSet());

        if (declaredInserts.size() > manifestInserts.size()) {
            logger.debug("DeclaredInserts: {}, ManifestInserts: {}", declaredInserts.size(), manifestInserts.size());
            throw new MoreInsertsThanExpectedError();
        }

        if (declaredInserts.size() < manifestInserts.size()) {
            logger.debug("DeclaredInserts: {}, ManifestInserts: {}", declaredInserts.size(), manifestInserts.size());
            throw new LessInsertsThanExpectedError();
        }

        if (!declaredInserts.containsAll(manifestInserts)) {
            throw new InsertMismatchError();
        }

        if (declaredDeletes.size() > manifestDeletes.size()) {
            logger.debug("DeclaredDeletes: {}, ManifestDeletes: {}", declaredDeletes.size(), manifestDeletes.size());
            throw new MoreDeletesThanExpectedError();
        }

        if (declaredDeletes.size() < manifestDeletes.size()) {
            logger.debug("DeclaredDeletes: {}, ManifestDeletes: {}", declaredDeletes.size(), manifestDeletes.size());
            throw new LessDeletesThanExpectedError();
        }

        if (!declaredDeletes.containsAll(manifestDeletes)) {
            throw new DeleteMismatchError();
        }
    }

    public static void validateForcePush(
            SignalStorageManifest manifest,
            List<SignalStorageRecord> inserts,
            RecipientAddress self
    ) {
        validateManifestAndInserts(manifest, inserts, self);
    }

    private static void validateManifestAndInserts(
            SignalStorageManifest manifest,
            List<SignalStorageRecord> inserts,
            RecipientAddress self
    ) {
        int accountCount = 0;
        for (StorageId id : manifest.storageIds) {
            accountCount += id.getType() == ManifestRecord.Identifier.Type.ACCOUNT.getValue() ? 1 : 0;
        }

        if (accountCount > 1) {
            throw new MultipleAccountError();
        }

        if (accountCount == 0) {
            throw new MissingAccountError();
        }

        Set<StorageId> allSet = new HashSet<>(manifest.storageIds);
        Set<StorageId> insertSet = inserts.stream().map(SignalStorageRecord::getId).collect(Collectors.toSet());
        Set<ByteBuffer> rawIdSet = allSet.stream().map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

        if (allSet.size() != manifest.storageIds.size()) {
            throw new DuplicateStorageIdError();
        }

        if (rawIdSet.size() != allSet.size()) {
            List<StorageId> ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.CONTACT.getValue());
            if (ids.size() != new HashSet<>(ids).size()) {
                throw new DuplicateContactIdError();
            }

            ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.GROUPV1.getValue());
            if (ids.size() != new HashSet<>(ids).size()) {
                throw new DuplicateGroupV1IdError();
            }

            ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.GROUPV2.getValue());
            if (ids.size() != new HashSet<>(ids).size()) {
                throw new DuplicateGroupV2IdError();
            }

            ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST.getValue());
            if (ids.size() != new HashSet<>(ids).size()) {
                throw new DuplicateDistributionListIdError();
            }

            ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.CALL_LINK.getValue());
            if (ids.size() != new HashSet<>(ids).size()) {
                throw new DuplicateCallLinkError();
            }

            throw new DuplicateRawIdAcrossTypesError();
        }

        if (inserts.size() > insertSet.size()) {
            throw new DuplicateInsertInWriteError();
        }

        for (SignalStorageRecord insert : inserts) {
            if (!allSet.contains(insert.getId())) {
                throw new InsertNotPresentInFullIdSetError();
            }

            if (insert.isUnknown()) {
                throw new UnknownInsertError();
            }

            if (insert.getProto().contact != null) {
                final var contact = insert.getProto().contact;
                final var aci = ACI.parseOrNull(contact.aci);
                final var pni = PNI.parseOrNull(contact.pni);
                final var number = contact.e164.isEmpty() ? null : contact.e164;
                final var username = contact.username.isEmpty() ? null : contact.username;
                final var address = new RecipientAddress(aci, pni, number, username);
                if (self.matches(address)) {
                    throw new SelfAddedAsContactError();
                }
            }
            if (insert.getProto().account != null && insert.getProto().account.profileKey.size() == 0) {
                logger.debug("Uploading a null profile key in our AccountRecord!");
            }
        }
    }

    private static final class DuplicateStorageIdError extends Error {}

    private static final class DuplicateRawIdAcrossTypesError extends Error {}

    private static final class DuplicateContactIdError extends Error {}

    private static final class DuplicateGroupV1IdError extends Error {}

    private static final class DuplicateGroupV2IdError extends Error {}

    private static final class DuplicateDistributionListIdError extends Error {}

    private static final class DuplicateCallLinkError extends Error {}

    private static final class DuplicateInsertInWriteError extends Error {}

    private static final class InsertNotPresentInFullIdSetError extends Error {}

    private static final class DeletePresentInFullIdSetError extends Error {}

    private static final class UnknownInsertError extends Error {}

    private static final class MultipleAccountError extends Error {}

    private static final class MissingAccountError extends Error {}

    private static final class SelfAddedAsContactError extends Error {}

    private static final class IncorrectManifestVersionError extends Error {}

    private static final class MoreInsertsThanExpectedError extends Error {}

    private static final class LessInsertsThanExpectedError extends Error {}

    private static final class InsertMismatchError extends Error {}

    private static final class MoreDeletesThanExpectedError extends Error {}

    private static final class LessDeletesThanExpectedError extends Error {}

    private static final class DeleteMismatchError extends Error {}
}
