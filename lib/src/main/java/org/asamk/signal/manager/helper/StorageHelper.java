package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class StorageHelper {

    private final static Logger logger = LoggerFactory.getLogger(StorageHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final GroupHelper groupHelper;
    private final ProfileHelper profileHelper;

    public StorageHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final GroupHelper groupHelper,
            final ProfileHelper profileHelper
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.groupHelper = groupHelper;
        this.profileHelper = profileHelper;
    }

    public void readDataFromStorage() throws IOException {
        logger.debug("Reading data from remote storage");
        Optional<SignalStorageManifest> manifest;
        try {
            manifest = dependencies.getAccountManager()
                    .getStorageManifestIfDifferentVersion(account.getStorageKey(), account.getStorageManifestVersion());
        } catch (InvalidKeyException e) {
            logger.warn("Manifest couldn't be decrypted, ignoring.");
            return;
        }

        if (!manifest.isPresent()) {
            logger.debug("Manifest is up to date, does not exist or couldn't be decrypted, ignoring.");
            return;
        }

        account.setStorageManifestVersion(manifest.get().getVersion());

        readAccountRecord(manifest.get());

        final var storageIds = manifest.get()
                .getStorageIds()
                .stream()
                .filter(id -> !id.isUnknown() && id.getType() != ManifestRecord.Identifier.Type.ACCOUNT_VALUE)
                .collect(Collectors.toList());

        for (final var record : getSignalStorageRecords(storageIds)) {
            if (record.getType() == ManifestRecord.Identifier.Type.GROUPV2_VALUE) {
                readGroupV2Record(record);
            } else if (record.getType() == ManifestRecord.Identifier.Type.GROUPV1_VALUE) {
                readGroupV1Record(record);
            } else if (record.getType() == ManifestRecord.Identifier.Type.CONTACT_VALUE) {
                readContactRecord(record);
            }
        }
    }

    private void readContactRecord(final SignalStorageRecord record) {
        if (record == null || !record.getContact().isPresent()) {
            return;
        }

        final var contactRecord = record.getContact().get();
        final var address = contactRecord.getAddress();

        final var recipientId = account.getRecipientStore().resolveRecipient(address);
        final var contact = account.getContactStore().getContact(recipientId);
        if (contactRecord.getGivenName().isPresent() || contactRecord.getFamilyName().isPresent() || (
                (contact == null || !contact.isBlocked()) && contactRecord.isBlocked()
        )) {
            final var newContact = (contact == null ? Contact.newBuilder() : Contact.newBuilder(contact)).withBlocked(
                    contactRecord.isBlocked())
                    .withName((contactRecord.getGivenName().or("") + " " + contactRecord.getFamilyName().or("")).trim())
                    .build();
            account.getContactStore().storeContact(recipientId, newContact);
        }

        if (contactRecord.getProfileKey().isPresent()) {
            try {
                final var profileKey = new ProfileKey(contactRecord.getProfileKey().get());
                account.getProfileStore().storeProfileKey(recipientId, profileKey);
            } catch (InvalidInputException e) {
                logger.warn("Received invalid contact profile key from storage");
            }
        }
        if (contactRecord.getIdentityKey().isPresent()) {
            try {
                final var identityKey = new IdentityKey(contactRecord.getIdentityKey().get());
                account.getIdentityKeyStore().saveIdentity(recipientId, identityKey, new Date());

                final var trustLevel = TrustLevel.fromIdentityState(contactRecord.getIdentityState());
                if (trustLevel != null) {
                    account.getIdentityKeyStore().setIdentityTrustLevel(recipientId, identityKey, trustLevel);
                }
            } catch (InvalidKeyException e) {
                logger.warn("Received invalid contact identity key from storage");
            }
        }
    }

    private void readGroupV1Record(final SignalStorageRecord record) {
        if (record == null || !record.getGroupV1().isPresent()) {
            return;
        }

        final var groupV1Record = record.getGroupV1().get();
        final var groupIdV1 = GroupId.v1(groupV1Record.getGroupId());

        final var group = account.getGroupStore().getGroup(groupIdV1);
        if (group == null) {
            try {
                groupHelper.sendGroupInfoRequest(groupIdV1, account.getSelfRecipientId());
            } catch (Throwable e) {
                logger.warn("Failed to send group request", e);
            }
        }
        final var groupV1 = account.getGroupStore().getOrCreateGroupV1(groupIdV1);
        if (groupV1.isBlocked() != groupV1Record.isBlocked()) {
            groupV1.setBlocked(groupV1Record.isBlocked());
            account.getGroupStore().updateGroup(groupV1);
        }
    }

    private void readGroupV2Record(final SignalStorageRecord record) {
        if (record == null || !record.getGroupV2().isPresent()) {
            return;
        }

        final var groupV2Record = record.getGroupV2().get();
        if (groupV2Record.isArchived()) {
            return;
        }

        final GroupMasterKey groupMasterKey;
        try {
            groupMasterKey = new GroupMasterKey(groupV2Record.getMasterKeyBytes());
        } catch (InvalidInputException e) {
            logger.warn("Received invalid group master key from storage");
            return;
        }

        final var group = groupHelper.getOrMigrateGroup(groupMasterKey, 0, null);
        if (group.isBlocked() != groupV2Record.isBlocked()) {
            group.setBlocked(groupV2Record.isBlocked());
            account.getGroupStore().updateGroup(group);
        }
    }

    private void readAccountRecord(final SignalStorageManifest manifest) throws IOException {
        Optional<StorageId> accountId = manifest.getAccountStorageId();
        if (!accountId.isPresent()) {
            logger.warn("Manifest has no account record, ignoring.");
            return;
        }

        SignalStorageRecord record = getSignalStorageRecord(accountId.get());
        if (record == null) {
            logger.warn("Could not find account record, even though we had an ID, ignoring.");
            return;
        }

        SignalAccountRecord accountRecord = record.getAccount().orNull();
        if (accountRecord == null) {
            logger.warn("The storage record didn't actually have an account, ignoring.");
            return;
        }

        if (!accountRecord.getE164().equals(account.getUsername())) {
            // TODO implement changed number handling
        }

        account.getConfigurationStore().setReadReceipts(accountRecord.isReadReceiptsEnabled());
        account.getConfigurationStore().setTypingIndicators(accountRecord.isTypingIndicatorsEnabled());
        account.getConfigurationStore()
                .setUnidentifiedDeliveryIndicators(accountRecord.isSealedSenderIndicatorsEnabled());
        account.getConfigurationStore().setLinkPreviews(accountRecord.isLinkPreviewsEnabled());

        if (accountRecord.getProfileKey().isPresent()) {
            ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(accountRecord.getProfileKey().get());
            } catch (InvalidInputException e) {
                logger.warn("Received invalid profile key from storage");
                profileKey = null;
            }
            if (profileKey != null) {
                account.setProfileKey(profileKey);
                final var avatarPath = accountRecord.getAvatarUrlPath().orNull();
                profileHelper.downloadProfileAvatar(account.getSelfRecipientId(), avatarPath, profileKey);
            }
        }

        profileHelper.setProfile(false,
                accountRecord.getGivenName().orNull(),
                accountRecord.getFamilyName().orNull(),
                null,
                null,
                null);
    }

    private SignalStorageRecord getSignalStorageRecord(final StorageId accountId) throws IOException {
        List<SignalStorageRecord> records;
        try {
            records = dependencies.getAccountManager()
                    .readStorageRecords(account.getStorageKey(), Collections.singletonList(accountId));
        } catch (InvalidKeyException e) {
            logger.warn("Failed to read storage records, ignoring.");
            return null;
        }
        return records.size() > 0 ? records.get(0) : null;
    }

    private List<SignalStorageRecord> getSignalStorageRecords(final List<StorageId> storageIds) throws IOException {
        List<SignalStorageRecord> records;
        try {
            records = dependencies.getAccountManager().readStorageRecords(account.getStorageKey(), storageIds);
        } catch (InvalidKeyException e) {
            logger.warn("Failed to read storage records, ignoring.");
            return List.of();
        }
        return records;
    }
}
