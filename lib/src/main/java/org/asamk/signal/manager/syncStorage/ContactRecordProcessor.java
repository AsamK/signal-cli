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
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import okio.ByteString;

import static org.asamk.signal.manager.util.Utils.firstNonEmpty;
import static org.asamk.signal.manager.util.Utils.nullIfEmpty;

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
    protected boolean isInvalid(SignalContactRecord remoteRecord) {
        final var remote = remoteRecord.getProto();
        final var aci = ACI.parseOrNull(remote.aci);
        final var pni = PNI.parseOrNull(remote.pni);
        final var e164 = nullIfEmpty(remote.e164);
        boolean hasAci = aci != null && aci.isValid();
        boolean hasPni = pni != null && pni.isValid();

        if (!hasAci && !hasPni) {
            logger.debug("Found a ContactRecord with neither an ACI nor a PNI -- marking as invalid.");
            return true;
        } else if (selfAci != null && selfAci.equals(aci) || (
                selfPni != null && selfPni.equals(pni)
        ) || (selfNumber != null && selfNumber.equals(e164))) {
            logger.debug("Found a ContactRecord for ourselves -- marking as invalid.");
            return true;
        } else if (e164 != null && !isValidE164(e164)) {
            logger.debug("Found a record with an invalid E164 ({}). Marking as invalid.", e164);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected Optional<SignalContactRecord> getMatching(SignalContactRecord remote) throws SQLException {
        final var address = getRecipientAddress(remote.getProto());
        final var recipientId = account.getRecipientStore().resolveRecipient(connection, address);
        final var recipient = account.getRecipientStore().getRecipient(connection, recipientId);

        final var identifier = recipient.getAddress().getIdentifier();
        final var identity = account.getIdentityKeyStore().getIdentityInfo(connection, identifier);
        final var storageId = account.getRecipientStore().getStorageId(connection, recipientId);

        return Optional.of(new SignalContactRecord(storageId,
                StorageSyncModels.localToRemoteRecord(recipient, identity)));
    }

    @Override
    protected SignalContactRecord merge(SignalContactRecord remoteRecord, SignalContactRecord localRecord) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();

        String profileGivenName;
        String profileFamilyName;
        if (!remote.givenName.isEmpty() || !remote.familyName.isEmpty()) {
            profileGivenName = remote.givenName;
            profileFamilyName = remote.familyName;
        } else {
            profileGivenName = local.givenName;
            profileFamilyName = local.familyName;
        }

        IdentityState identityState;
        ByteString identityKey;
        if (remote.identityKey.size() > 0 && (
                !account.isPrimaryDevice()
                        || remote.identityState != local.identityState
                        || local.identityKey.size() == 0

        )) {
            identityState = remote.identityState;
            identityKey = remote.identityKey;
        } else {
            identityState = local.identityState;
            identityKey = local.identityKey.size() > 0 ? local.identityKey : ByteString.EMPTY;
        }

        if (!local.aci.isEmpty()
                && local.identityKey.size() > 0
                && remote.identityKey.size() > 0
                && !local.identityKey.equals(remote.identityKey)) {
            logger.debug("The local and remote identity keys do not match for {}. Enqueueing a profile fetch.",
                    local.aci);
            final var address = getRecipientAddress(local);
            jobExecutor.enqueueJob(new DownloadProfileJob(address));
        }

        String pni;
        String e164;
        if (account.isPrimaryDevice()) {
            final var e164sMatchButPnisDont = !local.e164.isEmpty()
                    && local.e164.equals(remote.e164)
                    && !local.pni.isEmpty()
                    && !remote.pni.isEmpty()
                    && !local.pni.equals(remote.pni);

            final var pnisMatchButE164sDont = !local.pni.isEmpty()
                    && local.pni.equals(remote.pni)
                    && !local.e164.isEmpty()
                    && !remote.e164.isEmpty()
                    && !local.e164.equals(remote.e164);

            if (e164sMatchButPnisDont || pnisMatchButE164sDont) {
                if (e164sMatchButPnisDont) {
                    logger.debug("Matching E164s, but the PNIs differ! Trusting our local pair.");
                } else if (pnisMatchButE164sDont) {
                    logger.debug("Matching PNIs, but the E164s differ! Trusting our local pair.");
                }
                jobExecutor.enqueueJob(new RefreshRecipientsJob());
                pni = local.pni;
                e164 = local.e164;
            } else {
                pni = firstNonEmpty(remote.pni, local.pni);
                e164 = firstNonEmpty(remote.e164, local.e164);
            }
        } else {
            pni = firstNonEmpty(remote.pni, local.pni);
            e164 = firstNonEmpty(remote.e164, local.e164);
        }

        final var mergedBuilder = SignalContactRecord.Companion.newBuilder(remote.unknownFields().toByteArray())
                .aci(local.aci.isEmpty() ? remote.aci : local.aci)
                .e164(e164)
                .pni(pni)
                .givenName(profileGivenName)
                .familyName(profileFamilyName)
                .systemGivenName(account.isPrimaryDevice() ? local.systemGivenName : remote.systemGivenName)
                .systemFamilyName(account.isPrimaryDevice() ? local.systemFamilyName : remote.systemFamilyName)
                .systemNickname(remote.systemNickname)
                .profileKey(firstNonEmpty(remote.profileKey, local.profileKey))
                .username(firstNonEmpty(remote.username, local.username))
                .identityState(identityState)
                .identityKey(identityKey)
                .blocked(remote.blocked)
                .whitelisted(remote.whitelisted)
                .archived(remote.archived)
                .markedUnread(remote.markedUnread)
                .mutedUntilTimestamp(remote.mutedUntilTimestamp)
                .hideStory(remote.hideStory)
                .unregisteredAtTimestamp(remote.unregisteredAtTimestamp)
                .hidden(remote.hidden)
                .pniSignatureVerified(remote.pniSignatureVerified || local.pniSignatureVerified)
                .nickname(remote.nickname)
                .note(remote.note);
        final var merged = mergedBuilder.build();

        final var matchesRemote = doProtosMatch(merged, remote);
        if (matchesRemote) {
            return remoteRecord;
        }

        final var matchesLocal = doProtosMatch(merged, local);
        if (matchesLocal) {
            return localRecord;
        }

        return new SignalContactRecord(StorageId.forContact(KeyUtils.createRawStorageId()), mergedBuilder.build());
    }

    @Override
    protected void insertLocal(SignalContactRecord record) throws SQLException {
        StorageRecordUpdate<SignalContactRecord> update = new StorageRecordUpdate<>(null, record);
        updateLocal(update);
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalContactRecord> update) throws SQLException {
        final var contactRecord = update.newRecord();
        final var contactProto = contactRecord.getProto();
        final var address = getRecipientAddress(contactProto);
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
        final var contactNickGivenName = contact == null ? null : contact.nickNameGivenName();
        final var contactNickFamilyName = contact == null ? null : contact.nickNameFamilyName();
        final var contactNote = contact == null ? null : contact.note();
        if (blocked != contactProto.blocked
                || profileShared != contactProto.whitelisted
                || archived != contactProto.archived
                || hidden != contactProto.hidden
                || hideStory != contactProto.hideStory
                || muteUntil != contactProto.mutedUntilTimestamp
                || unregisteredTimestamp != contactProto.unregisteredAtTimestamp
                || !Objects.equals(nullIfEmpty(contactProto.systemGivenName), contactGivenName)
                || !Objects.equals(nullIfEmpty(contactProto.systemFamilyName), contactFamilyName)
                || !Objects.equals(nullIfEmpty(contactProto.systemNickname), contactNickName)
                || !Objects.equals(nullIfEmpty(contactProto.nickname == null ? "" : contactProto.nickname.given),
                contactNickGivenName)
                || !Objects.equals(nullIfEmpty(contactProto.nickname == null ? "" : contactProto.nickname.family),
                contactNickFamilyName)
                || !Objects.equals(nullIfEmpty(contactProto.note), contactNote)) {
            logger.debug("Storing new or updated contact {}", recipientId);
            final var contactBuilder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
            final var newContact = contactBuilder.withIsBlocked(contactProto.blocked)
                    .withIsProfileSharingEnabled(contactProto.whitelisted)
                    .withIsArchived(contactProto.archived)
                    .withIsHidden(contactProto.hidden)
                    .withMuteUntil(contactProto.mutedUntilTimestamp)
                    .withHideStory(contactProto.hideStory)
                    .withGivenName(nullIfEmpty(contactProto.systemGivenName))
                    .withFamilyName(nullIfEmpty(contactProto.systemFamilyName))
                    .withNickName(nullIfEmpty(contactProto.systemNickname))
                    .withNickNameGivenName(nullIfEmpty(contactProto.givenName))
                    .withNickNameFamilyName(nullIfEmpty(contactProto.familyName))
                    .withNote(nullIfEmpty(contactProto.note))
                    .withUnregisteredTimestamp(contactProto.unregisteredAtTimestamp);
            account.getRecipientStore().storeContact(connection, recipientId, newContact.build());
        }

        final var profile = recipient.getProfile();
        final var profileGivenName = profile == null ? null : profile.getGivenName();
        final var profileFamilyName = profile == null ? null : profile.getFamilyName();
        if (!Objects.equals(nullIfEmpty(contactProto.givenName), profileGivenName) || !Objects.equals(nullIfEmpty(
                contactProto.familyName), profileFamilyName)) {
            final var profileBuilder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
            final var newProfile = profileBuilder.withGivenName(nullIfEmpty(contactProto.givenName))
                    .withFamilyName(nullIfEmpty(contactProto.familyName))
                    .build();
            account.getRecipientStore().storeProfile(connection, recipientId, newProfile);
        }
        if (contactProto.profileKey.size() > 0) {
            try {
                logger.trace("Storing profile key {}", recipientId);
                final var profileKey = new ProfileKey(contactProto.profileKey.toByteArray());
                account.getRecipientStore().storeProfileKey(connection, recipientId, profileKey);
            } catch (InvalidInputException e) {
                logger.warn("Received invalid contact profile key from storage");
            }
        }
        if (contactProto.identityKey.size() > 0 && address.aci().isPresent()) {
            try {
                logger.trace("Storing identity key {}", recipientId);
                final var identityKey = new IdentityKey(contactProto.identityKey.toByteArray());
                account.getIdentityKeyStore().saveIdentity(connection, address.aci().get(), identityKey);

                final var trustLevel = StorageSyncModels.remoteToLocal(contactProto.identityState);
                if (trustLevel != null) {
                    account.getIdentityKeyStore()
                            .setIdentityTrustLevel(connection, address.aci().get(), identityKey, trustLevel);
                }
            } catch (InvalidKeyException e) {
                logger.warn("Received invalid contact identity key from storage");
            }
        }
        account.getRecipientStore()
                .storeStorageRecord(connection, recipientId, contactRecord.getId(), contactProto.encode());
    }

    private static RecipientAddress getRecipientAddress(final ContactRecord contactRecord) {
        return new RecipientAddress(ACI.parseOrNull(contactRecord.aci),
                PNI.parseOrNull(contactRecord.pni),
                nullIfEmpty(contactRecord.e164),
                nullIfEmpty(contactRecord.username));
    }

    @Override
    public int compare(SignalContactRecord lhsRecord, SignalContactRecord rhsRecord) {
        final var lhs = lhsRecord.getProto();
        final var rhs = rhsRecord.getProto();
        if ((!lhs.aci.isEmpty() && Objects.equals(lhs.aci, rhs.aci)) || (
                !lhs.e164.isEmpty() && Objects.equals(lhs.e164, rhs.e164)
        ) || (!lhs.pni.isEmpty() && Objects.equals(lhs.pni, rhs.pni))) {
            return 0;
        } else {
            return 1;
        }
    }

    private static boolean isValidE164(String value) {
        return E164_PATTERN.matcher(value).matches();
    }

    private static boolean doProtosMatch(ContactRecord merged, ContactRecord other) {
        return Arrays.equals(merged.encode(), other.encode());
    }
}
