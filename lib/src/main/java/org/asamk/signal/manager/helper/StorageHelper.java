package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class StorageHelper {

    private final static Logger logger = LoggerFactory.getLogger(StorageHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    public StorageHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public void readDataFromStorage() throws IOException {
        final var storageKey = account.getOrCreateStorageKey();
        if (storageKey == null) {
            logger.debug("Storage key unknown, requesting from primary device.");
            context.getSyncHelper().requestSyncKeys();
            return;
        }

        logger.debug("Reading data from remote storage");
        Optional<SignalStorageManifest> manifest;
        try {
            manifest = dependencies.getAccountManager()
                    .getStorageManifestIfDifferentVersion(storageKey, account.getStorageManifestVersion());
        } catch (InvalidKeyException e) {
            logger.warn("Manifest couldn't be decrypted, ignoring.");
            return;
        }

        if (manifest.isEmpty()) {
            logger.debug("Manifest is up to date, does not exist or couldn't be decrypted, ignoring.");
            return;
        }

        logger.trace("Remote storage manifest has {} records", manifest.get().getStorageIds().size());
        final var storageIds = manifest.get()
                .getStorageIds()
                .stream()
                .filter(id -> !id.isUnknown())
                .collect(Collectors.toSet());

        Optional<SignalStorageManifest> localManifest = account.getStorageManifest();
        localManifest.ifPresent(m -> m.getStorageIds().forEach(storageIds::remove));

        logger.trace("Reading {} new records", manifest.get().getStorageIds().size());
        for (final var record : getSignalStorageRecords(storageIds)) {
            logger.debug("Reading record of type {}", record.getType());
            if (record.getType() == ManifestRecord.Identifier.Type.ACCOUNT_VALUE) {
                readAccountRecord(record);
            } else if (record.getType() == ManifestRecord.Identifier.Type.GROUPV2_VALUE) {
                readGroupV2Record(record);
            } else if (record.getType() == ManifestRecord.Identifier.Type.GROUPV1_VALUE) {
                readGroupV1Record(record);
            } else if (record.getType() == ManifestRecord.Identifier.Type.CONTACT_VALUE) {
                readContactRecord(record);
            }
        }
        account.setStorageManifestVersion(manifest.get().getVersion());
        account.setStorageManifest(manifest.get());
        logger.debug("Done reading data from remote storage");
    }

    private void readContactRecord(final SignalStorageRecord record) {
        if (record == null || record.getContact().isEmpty()) {
            return;
        }

        final var contactRecord = record.getContact().get();
        final var serviceId = contactRecord.getServiceId();
        if (contactRecord.getNumber().isEmpty() && serviceId.isUnknown()) {
            return;
        }
        final var address = new RecipientAddress(serviceId, contactRecord.getNumber().orElse(null));
        var recipientId = account.getRecipientResolver().resolveRecipient(address);
        if (serviceId.isValid() && contactRecord.getUsername().isPresent()) {
            recipientId = account.getRecipientTrustedResolver()
                    .resolveRecipientTrusted(serviceId, contactRecord.getUsername().get());
        }

        final var contact = account.getContactStore().getContact(recipientId);
        final var blocked = contact != null && contact.isBlocked();
        final var profileShared = contact != null && contact.isProfileSharingEnabled();
        final var archived = contact != null && contact.isArchived();
        final var contactGivenName = contact == null ? null : contact.getGivenName();
        final var contactFamilyName = contact == null ? null : contact.getFamilyName();
        if (blocked != contactRecord.isBlocked()
                || profileShared != contactRecord.isProfileSharingEnabled()
                || archived != contactRecord.isArchived()
                || (
                contactRecord.getSystemGivenName().isPresent() && !contactRecord.getSystemGivenName()
                        .get()
                        .equals(contactGivenName)
        )
                || (
                contactRecord.getSystemFamilyName().isPresent() && !contactRecord.getSystemFamilyName()
                        .get()
                        .equals(contactFamilyName)
        )) {
            logger.debug("Storing new or updated contact {}", recipientId);
            final var contactBuilder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            final var newContact = contactBuilder.withBlocked(contactRecord.isBlocked())
                    .withProfileSharingEnabled(contactRecord.isProfileSharingEnabled())
                    .withArchived(contactRecord.isArchived());
            if (contactRecord.getSystemGivenName().isPresent() || contactRecord.getSystemFamilyName().isPresent()) {
                newContact.withGivenName(contactRecord.getSystemGivenName().orElse(null))
                        .withFamilyName(contactRecord.getSystemFamilyName().orElse(null));
            }
            account.getContactStore().storeContact(recipientId, newContact.build());
        }

        final var profile = account.getProfileStore().getProfile(recipientId);
        final var profileGivenName = profile == null ? null : profile.getGivenName();
        final var profileFamilyName = profile == null ? null : profile.getFamilyName();
        if ((
                contactRecord.getProfileGivenName().isPresent() && !contactRecord.getProfileGivenName()
                        .get()
                        .equals(profileGivenName)
        ) || (
                contactRecord.getProfileFamilyName().isPresent() && !contactRecord.getProfileFamilyName()
                        .get()
                        .equals(profileFamilyName)
        )) {
            final var profileBuilder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
            final var newProfile = profileBuilder.withGivenName(contactRecord.getProfileGivenName().orElse(null))
                    .withFamilyName(contactRecord.getProfileFamilyName().orElse(null))
                    .build();
            account.getProfileStore().storeProfile(recipientId, newProfile);
        }
        if (contactRecord.getProfileKey().isPresent()) {
            try {
                logger.trace("Storing profile key {}", recipientId);
                final var profileKey = new ProfileKey(contactRecord.getProfileKey().get());
                account.getProfileStore().storeProfileKey(recipientId, profileKey);
            } catch (InvalidInputException e) {
                logger.warn("Received invalid contact profile key from storage");
            }
        }
        if (contactRecord.getIdentityKey().isPresent() && serviceId.isValid()) {
            try {
                logger.trace("Storing identity key {}", recipientId);
                final var identityKey = new IdentityKey(contactRecord.getIdentityKey().get());
                account.getIdentityKeyStore().saveIdentity(serviceId, identityKey);

                final var trustLevel = TrustLevel.fromIdentityState(contactRecord.getIdentityState());
                if (trustLevel != null) {
                    account.getIdentityKeyStore().setIdentityTrustLevel(serviceId, identityKey, trustLevel);
                }
            } catch (InvalidKeyException e) {
                logger.warn("Received invalid contact identity key from storage");
            }
        }
    }

    private void readGroupV1Record(final SignalStorageRecord record) {
        if (record == null || record.getGroupV1().isEmpty()) {
            return;
        }

        final var groupV1Record = record.getGroupV1().get();
        final var groupIdV1 = GroupId.v1(groupV1Record.getGroupId());

        var group = account.getGroupStore().getGroup(groupIdV1);
        if (group == null) {
            try {
                context.getGroupHelper().sendGroupInfoRequest(groupIdV1, account.getSelfRecipientId());
            } catch (Throwable e) {
                logger.warn("Failed to send group request", e);
            }
            group = account.getGroupStore().getOrCreateGroupV1(groupIdV1);
        }
        if (group != null && group.isBlocked() != groupV1Record.isBlocked()) {
            group.setBlocked(groupV1Record.isBlocked());
            account.getGroupStore().updateGroup(group);
        }
    }

    private void readGroupV2Record(final SignalStorageRecord record) {
        if (record == null || record.getGroupV2().isEmpty()) {
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

        final var group = context.getGroupHelper().getOrMigrateGroup(groupMasterKey, 0, null);
        if (group.isBlocked() != groupV2Record.isBlocked()) {
            group.setBlocked(groupV2Record.isBlocked());
            account.getGroupStore().updateGroup(group);
        }
    }

    private void readAccountRecord(final SignalStorageRecord record) throws IOException {
        if (record == null) {
            logger.warn("Could not find account record, even though we had an ID, ignoring.");
            return;
        }

        SignalAccountRecord accountRecord = record.getAccount().orElse(null);
        if (accountRecord == null) {
            logger.warn("The storage record didn't actually have an account, ignoring.");
            return;
        }

        if (!accountRecord.getE164().equals(account.getNumber())) {
            context.getAccountHelper().checkWhoAmiI();
        }

        account.getConfigurationStore().setReadReceipts(accountRecord.isReadReceiptsEnabled());
        account.getConfigurationStore().setTypingIndicators(accountRecord.isTypingIndicatorsEnabled());
        account.getConfigurationStore()
                .setUnidentifiedDeliveryIndicators(accountRecord.isSealedSenderIndicatorsEnabled());
        account.getConfigurationStore().setLinkPreviews(accountRecord.isLinkPreviewsEnabled());
        if (accountRecord.getPhoneNumberSharingMode() != AccountRecord.PhoneNumberSharingMode.UNRECOGNIZED) {
            account.getConfigurationStore()
                    .setPhoneNumberSharingMode(switch (accountRecord.getPhoneNumberSharingMode()) {
                        case EVERYBODY -> PhoneNumberSharingMode.EVERYBODY;
                        case NOBODY -> PhoneNumberSharingMode.NOBODY;
                        default -> PhoneNumberSharingMode.CONTACTS;
                    });
        }
        account.getConfigurationStore().setPhoneNumberUnlisted(accountRecord.isPhoneNumberUnlisted());
        account.setUsername(accountRecord.getUsername());

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
                final var avatarPath = accountRecord.getAvatarUrlPath().orElse(null);
                context.getProfileHelper().downloadProfileAvatar(account.getSelfRecipientId(), avatarPath, profileKey);
            }
        }

        context.getProfileHelper()
                .setProfile(false,
                        false,
                        accountRecord.getGivenName().orElse(null),
                        accountRecord.getFamilyName().orElse(null),
                        null,
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

    private List<SignalStorageRecord> getSignalStorageRecords(final Collection<StorageId> storageIds) throws IOException {
        List<SignalStorageRecord> records;
        try {
            records = dependencies.getAccountManager()
                    .readStorageRecords(account.getStorageKey(), new ArrayList<>(storageIds));
        } catch (InvalidKeyException e) {
            logger.warn("Failed to read storage records, ignoring.");
            return List.of();
        }
        return records;
    }
}
