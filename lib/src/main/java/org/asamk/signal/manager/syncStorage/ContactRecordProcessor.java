package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.internal.JobExecutor;
import org.asamk.signal.manager.jobs.DownloadProfileJob;
import org.asamk.signal.manager.jobs.RefreshRecipientsJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class ContactRecordProcessor extends DefaultStorageRecordProcessor<SignalContactRecord> {

    private static final Logger logger = LoggerFactory.getLogger(ContactRecordProcessor.class);

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{0,18}$");

    private final ACI selfAci;
    private final PNI selfPni;
    private final String selfNumber;
    private final SignalAccount account;
    private final Connection connection;
    private final JobExecutor jobExecutor;

    public ContactRecordProcessor(SignalAccount account, Connection connection, final JobExecutor jobExecutor) {
        this.account = account;
        this.connection = connection;
        this.jobExecutor = jobExecutor;
        this.selfAci = account.getAci();
        this.selfPni = account.getPni();
        this.selfNumber = account.getNumber();
    }

    /**
     * Error cases:
     * - You can't have a contact record without an ACI or PNI.
     * - You can't have a contact record for yourself. That should be an account record.
     */
    @Override
    protected boolean isInvalid(SignalContactRecord remote) {
        boolean hasAci = remote.getAci().isPresent() && remote.getAci().get().isValid();
        boolean hasPni = remote.getPni().isPresent() && remote.getPni().get().isValid();

        if (!hasAci && !hasPni) {
            logger.debug("Found a ContactRecord with neither an ACI nor a PNI -- marking as invalid.");
            return true;
        } else if (selfAci != null && selfAci.equals(remote.getAci().orElse(null)) || (
                selfPni != null && selfPni.equals(remote.getPni().orElse(null))
        ) || (selfNumber != null && selfNumber.equals(remote.getNumber().orElse(null)))) {
            logger.debug("Found a ContactRecord for ourselves -- marking as invalid.");
            return true;
        } else if (remote.getNumber().isPresent() && !isValidE164(remote.getNumber().get())) {
            logger.debug("Found a record with an invalid E164. Marking as invalid.");
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected Optional<SignalContactRecord> getMatching(SignalContactRecord remote) throws SQLException {
        final var address = getRecipientAddress(remote);
        final var recipientId = account.getRecipientStore().resolveRecipient(connection, address);
        final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);

        final var identifier = recipient.getAddress().getIdentifier();
        final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, identifier);
        final var storageId = account.getRecipientStore().getStorageId(connection, recipientId);

        return Optional.of(StorageSyncModels.localToRemoteRecord(recipient, identity, storageId.getRaw())
                .getContact()
                .get());
    }

    @Override
    protected SignalContactRecord merge(
            SignalContactRecord remote, SignalContactRecord local
    ) {
        String profileGivenName;
        String profileFamilyName;
        if (remote.getProfileGivenName().isPresent() || remote.getProfileFamilyName().isPresent()) {
            profileGivenName = remote.getProfileGivenName().orElse("");
            profileFamilyName = remote.getProfileFamilyName().orElse("");
        } else {
            profileGivenName = local.getProfileGivenName().orElse("");
            profileFamilyName = local.getProfileFamilyName().orElse("");
        }

        IdentityState identityState;
        byte[] identityKey;
        if (remote.getIdentityKey().isPresent() && (
                remote.getIdentityState() != local.getIdentityState()
                        || local.getIdentityKey().isEmpty()
                        || !account.isPrimaryDevice()

        )) {
            identityState = remote.getIdentityState();
            identityKey = remote.getIdentityKey().get();
        } else {
            identityState = local.getIdentityState();
            identityKey = local.getIdentityKey().orElse(null);
        }

        if (local.getAci().isPresent()
                && local.getIdentityKey().isPresent()
                && remote.getIdentityKey().isPresent()
                && !Arrays.equals(local.getIdentityKey().get(), remote.getIdentityKey().get())) {
            logger.debug("The local and remote identity keys do not match for {}. Enqueueing a profile fetch.",
                    local.getAci().orElse(null));
            final var address = getRecipientAddress(local);
            jobExecutor.enqueueJob(new DownloadProfileJob(address));
        }

        final var e164sMatchButPnisDont = local.getNumber().isPresent()
                && local.getNumber()
                .get()
                .equals(remote.getNumber().orElse(null))
                && local.getPni().isPresent()
                && remote.getPni().isPresent()
                && !local.getPni().get().equals(remote.getPni().get());

        final var pnisMatchButE164sDont = local.getPni().isPresent()
                && local.getPni()
                .get()
                .equals(remote.getPni().orElse(null))
                && local.getNumber().isPresent()
                && remote.getNumber().isPresent()
                && !local.getNumber().get().equals(remote.getNumber().get());

        PNI pni;
        String e164;
        if (!account.isPrimaryDevice() && (e164sMatchButPnisDont || pnisMatchButE164sDont)) {
            if (e164sMatchButPnisDont) {
                logger.debug("Matching E164s, but the PNIs differ! Trusting our local pair.");
            } else if (pnisMatchButE164sDont) {
                logger.debug("Matching PNIs, but the E164s differ! Trusting our local pair.");
            }
            jobExecutor.enqueueJob(new RefreshRecipientsJob());
            pni = local.getPni().get();
            e164 = local.getNumber().get();
        } else {
            pni = OptionalUtil.or(remote.getPni(), local.getPni()).orElse(null);
            e164 = OptionalUtil.or(remote.getNumber(), local.getNumber()).orElse(null);
        }

        final var unknownFields = remote.serializeUnknownFields();
        final var aci = local.getAci().isEmpty() ? remote.getAci().orElse(null) : local.getAci().get();
        final var profileKey = OptionalUtil.or(remote.getProfileKey(), local.getProfileKey()).orElse(null);
        final var username = OptionalUtil.or(remote.getUsername(), local.getUsername()).orElse("");
        final var blocked = remote.isBlocked();
        final var profileSharing = remote.isProfileSharingEnabled();
        final var archived = remote.isArchived();
        final var forcedUnread = remote.isForcedUnread();
        final var muteUntil = remote.getMuteUntil();
        final var hideStory = remote.shouldHideStory();
        final var unregisteredTimestamp = remote.getUnregisteredTimestamp();
        final var hidden = remote.isHidden();
        final var systemGivenName = account.isPrimaryDevice()
                ? local.getSystemGivenName().orElse("")
                : remote.getSystemGivenName().orElse("");
        final var systemFamilyName = account.isPrimaryDevice()
                ? local.getSystemFamilyName().orElse("")
                : remote.getSystemFamilyName().orElse("");
        final var systemNickname = remote.getSystemNickname().orElse("");
        final var pniSignatureVerified = remote.isPniSignatureVerified() || local.isPniSignatureVerified();

        final var mergedBuilder = new SignalContactRecord.Builder(remote.getId().getRaw(), aci, unknownFields).setE164(
                        e164)
                .setPni(pni)
                .setProfileGivenName(profileGivenName)
                .setProfileFamilyName(profileFamilyName)
                .setSystemGivenName(systemGivenName)
                .setSystemFamilyName(systemFamilyName)
                .setSystemNickname(systemNickname)
                .setProfileKey(profileKey)
                .setUsername(username)
                .setIdentityState(identityState)
                .setIdentityKey(identityKey)
                .setBlocked(blocked)
                .setProfileSharingEnabled(profileSharing)
                .setArchived(archived)
                .setForcedUnread(forcedUnread)
                .setMuteUntil(muteUntil)
                .setHideStory(hideStory)
                .setUnregisteredTimestamp(unregisteredTimestamp)
                .setHidden(hidden)
                .setPniSignatureVerified(pniSignatureVerified);
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
    protected void insertLocal(SignalContactRecord record) throws SQLException {
        StorageRecordUpdate<SignalContactRecord> update = new StorageRecordUpdate<>(null, record);
        updateLocal(update);
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalContactRecord> update) throws SQLException {
        final var contactRecord = update.newRecord();
        final var address = getRecipientAddress(contactRecord);
        final var recipientId = account.getRecipientStore().resolveRecipientTrusted(connection, address);
        final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);

        final var contact = recipient.getContact();
        final var blocked = contact != null && contact.isBlocked();
        final var profileShared = contact != null && contact.isProfileSharingEnabled();
        final var archived = contact != null && contact.isArchived();
        final var hidden = contact != null && contact.isHidden();
        final var hideStory = contact != null && contact.hideStory();
        final var muteUntil = contact == null ? 0 : contact.muteUntil();
        final var unregisteredTimestamp = contact == null || contact.unregisteredTimestamp() == null
                ? 0
                : contact.unregisteredTimestamp();
        final var contactGivenName = contact == null ? null : contact.givenName();
        final var contactFamilyName = contact == null ? null : contact.familyName();
        final var contactNickName = contact == null ? null : contact.nickName();
        if (blocked != contactRecord.isBlocked()
                || profileShared != contactRecord.isProfileSharingEnabled()
                || archived != contactRecord.isArchived()
                || hidden != contactRecord.isHidden()
                || hideStory != contactRecord.shouldHideStory()
                || muteUntil != contactRecord.getMuteUntil()
                || unregisteredTimestamp != contactRecord.getUnregisteredTimestamp()
                || !Objects.equals(contactRecord.getSystemGivenName().orElse(null), contactGivenName)
                || !Objects.equals(contactRecord.getSystemFamilyName().orElse(null), contactFamilyName)
                || !Objects.equals(contactRecord.getSystemNickname().orElse(null), contactNickName)) {
            logger.debug("Storing new or updated contact {}", recipientId);
            final var contactBuilder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            final var newContact = contactBuilder.withIsBlocked(contactRecord.isBlocked())
                    .withIsProfileSharingEnabled(contactRecord.isProfileSharingEnabled())
                    .withIsArchived(contactRecord.isArchived())
                    .withIsHidden(contactRecord.isHidden())
                    .withMuteUntil(contactRecord.getMuteUntil())
                    .withHideStory(contactRecord.shouldHideStory())
                    .withGivenName(contactRecord.getSystemGivenName().orElse(null))
                    .withFamilyName(contactRecord.getSystemFamilyName().orElse(null))
                    .withNickName(contactRecord.getSystemNickname().orElse(null))
                    .withUnregisteredTimestamp(contactRecord.getUnregisteredTimestamp() == 0
                            ? null
                            : contactRecord.getUnregisteredTimestamp());
            account.getRecipientStore().storeContact(connection, recipientId, newContact.build());
        }

        final var profile = recipient.getProfile();
        final var profileGivenName = profile == null ? null : profile.getGivenName();
        final var profileFamilyName = profile == null ? null : profile.getFamilyName();
        if (!Objects.equals(contactRecord.getProfileGivenName().orElse(null), profileGivenName) || !Objects.equals(
                contactRecord.getProfileFamilyName().orElse(null),
                profileFamilyName)) {
            final var profileBuilder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
            final var newProfile = profileBuilder.withGivenName(contactRecord.getProfileGivenName().orElse(null))
                    .withFamilyName(contactRecord.getProfileFamilyName().orElse(null))
                    .build();
            account.getRecipientStore().storeProfile(connection, recipientId, newProfile);
        }
        if (contactRecord.getProfileKey().isPresent()) {
            try {
                logger.trace("Storing profile key {}", recipientId);
                final var profileKey = new ProfileKey(contactRecord.getProfileKey().get());
                account.getRecipientStore().storeProfileKey(connection, recipientId, profileKey);
            } catch (InvalidInputException e) {
                logger.warn("Received invalid contact profile key from storage");
            }
        }
        if (contactRecord.getIdentityKey().isPresent() && contactRecord.getAci().isPresent()) {
            try {
                logger.trace("Storing identity key {}", recipientId);
                final var identityKey = new IdentityKey(contactRecord.getIdentityKey().get());
                account.getIdentityKeyStore()
                        .saveIdentity(connection, contactRecord.getAci().orElse(null), identityKey);

                final var trustLevel = StorageSyncModels.remoteToLocal(contactRecord.getIdentityState());
                if (trustLevel != null) {
                    account.getIdentityKeyStore()
                            .setIdentityTrustLevel(connection,
                                    contactRecord.getAci().orElse(null),
                                    identityKey,
                                    trustLevel);
                }
            } catch (InvalidKeyException e) {
                logger.warn("Received invalid contact identity key from storage");
            }
        }
        account.getRecipientStore()
                .storeStorageRecord(connection, recipientId, contactRecord.getId(), contactRecord.toProto().encode());
    }

    private static RecipientAddress getRecipientAddress(final SignalContactRecord contactRecord) {
        return new RecipientAddress(contactRecord.getAci().orElse(null),
                contactRecord.getPni().orElse(null),
                contactRecord.getNumber().orElse(null),
                contactRecord.getUsername().orElse(null));
    }

    @Override
    public int compare(SignalContactRecord lhs, SignalContactRecord rhs) {
        if ((lhs.getAci().isPresent() && Objects.equals(lhs.getAci(), rhs.getAci())) || (
                lhs.getNumber().isPresent() && Objects.equals(lhs.getNumber(), rhs.getNumber())
        ) || (lhs.getPni().isPresent() && Objects.equals(lhs.getPni(), rhs.getPni()))) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean isValidE164(String value) {
        return E164_PATTERN.matcher(value).matches();
    }

    private static boolean doProtosMatch(SignalContactRecord merged, SignalContactRecord other) {
        return Arrays.equals(merged.toProto().encode(), other.toProto().encode());
    }
}
