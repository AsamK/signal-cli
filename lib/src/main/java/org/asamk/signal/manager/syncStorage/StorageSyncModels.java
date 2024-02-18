package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.configuration.ConfigurationStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.recipients.Recipient;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord.UsernameLink;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Optional;

import okio.ByteString;

public final class StorageSyncModels {

    private StorageSyncModels() {
    }

    public static AccountRecord.PhoneNumberSharingMode localToRemote(PhoneNumberSharingMode phoneNumberPhoneNumberSharingMode) {
        return switch (phoneNumberPhoneNumberSharingMode) {
            case EVERYBODY -> AccountRecord.PhoneNumberSharingMode.EVERYBODY;
            case CONTACTS, NOBODY -> AccountRecord.PhoneNumberSharingMode.NOBODY;
        };
    }

    public static PhoneNumberSharingMode remoteToLocal(AccountRecord.PhoneNumberSharingMode phoneNumberPhoneNumberSharingMode) {
        return switch (phoneNumberPhoneNumberSharingMode) {
            case EVERYBODY -> PhoneNumberSharingMode.EVERYBODY;
            case UNKNOWN, NOBODY -> PhoneNumberSharingMode.NOBODY;
        };
    }

    public static SignalStorageRecord localToRemoteRecord(
            ConfigurationStore configStore,
            Recipient self,
            UsernameLinkComponents usernameLinkComponents,
            byte[] rawStorageId
    ) {
        final var builder = new SignalAccountRecord.Builder(rawStorageId, self.getStorageRecord());
        if (self.getProfileKey() != null) {
            builder.setProfileKey(self.getProfileKey().serialize());
        }
        if (self.getProfile() != null) {
            builder.setGivenName(self.getProfile().getGivenName())
                    .setFamilyName(self.getProfile().getFamilyName())
                    .setAvatarUrlPath(self.getProfile().getAvatarUrlPath());
        }
        builder.setTypingIndicatorsEnabled(Optional.ofNullable(configStore.getTypingIndicators()).orElse(true))
                .setReadReceiptsEnabled(Optional.ofNullable(configStore.getReadReceipts()).orElse(true))
                .setSealedSenderIndicatorsEnabled(Optional.ofNullable(configStore.getUnidentifiedDeliveryIndicators())
                        .orElse(true))
                .setLinkPreviewsEnabled(Optional.ofNullable(configStore.getLinkPreviews()).orElse(true))
                .setUnlistedPhoneNumber(Optional.ofNullable(configStore.getPhoneNumberUnlisted()).orElse(false))
                .setPhoneNumberSharingMode(localToRemote(Optional.ofNullable(configStore.getPhoneNumberSharingMode())
                        .orElse(PhoneNumberSharingMode.EVERYBODY)))
                .setE164(self.getAddress().number().orElse(""))
                .setUsername(self.getAddress().username().orElse(null));
        if (usernameLinkComponents != null) {
            final var linkColor = configStore.getUsernameLinkColor();
            builder.setUsernameLink(new UsernameLink.Builder().entropy(ByteString.of(usernameLinkComponents.getEntropy()))
                    .serverId(UuidUtil.toByteString(usernameLinkComponents.getServerId()))
                    .color(linkColor == null ? UsernameLink.Color.UNKNOWN : UsernameLink.Color.valueOf(linkColor))
                    .build());
        }

        return SignalStorageRecord.forAccount(builder.build());
    }

    public static SignalStorageRecord localToRemoteRecord(
            Recipient recipient, IdentityInfo identity, byte[] rawStorageId
    ) {
        final var address = recipient.getAddress();
        final var builder = new SignalContactRecord.Builder(rawStorageId,
                address.aci().orElse(null),
                recipient.getStorageRecord()).setE164(address.number().orElse(null))
                .setPni(address.pni().orElse(null))
                .setUsername(address.username().orElse(null))
                .setProfileKey(recipient.getProfileKey() == null ? null : recipient.getProfileKey().serialize());
        if (recipient.getProfile() != null) {
            builder.setProfileGivenName(recipient.getProfile().getGivenName())
                    .setProfileFamilyName(recipient.getProfile().getFamilyName());
        }
        if (recipient.getContact() != null) {
            builder.setSystemGivenName(recipient.getContact().givenName())
                    .setSystemFamilyName(recipient.getContact().familyName())
                    .setSystemNickname(recipient.getContact().nickName())
                    .setBlocked(recipient.getContact().isBlocked())
                    .setProfileSharingEnabled(recipient.getContact().isProfileSharingEnabled())
                    .setMuteUntil(recipient.getContact().muteUntil())
                    .setHideStory(recipient.getContact().hideStory())
                    .setUnregisteredTimestamp(recipient.getContact().unregisteredTimestamp() == null
                            ? 0
                            : recipient.getContact().unregisteredTimestamp())
                    .setArchived(recipient.getContact().isArchived())
                    .setHidden(recipient.getContact().isHidden());
        }
        if (identity != null) {
            builder.setIdentityKey(identity.getIdentityKey().serialize())
                    .setIdentityState(localToRemote(identity.getTrustLevel()));
        }
        return SignalStorageRecord.forContact(builder.build());
    }

    public static SignalStorageRecord localToRemoteRecord(
            GroupInfoV1 group, byte[] rawStorageId
    ) {
        final var builder = new SignalGroupV1Record.Builder(rawStorageId,
                group.getGroupId().serialize(),
                group.getStorageRecord());
        builder.setBlocked(group.isBlocked());
        builder.setArchived(group.archived);
        builder.setProfileSharingEnabled(true);
        return SignalStorageRecord.forGroupV1(builder.build());
    }

    public static SignalStorageRecord localToRemoteRecord(
            GroupInfoV2 group, byte[] rawStorageId
    ) {
        final var builder = new SignalGroupV2Record.Builder(rawStorageId,
                group.getMasterKey(),
                group.getStorageRecord());
        builder.setBlocked(group.isBlocked());
        builder.setProfileSharingEnabled(group.isProfileSharingEnabled());
        return SignalStorageRecord.forGroupV2(builder.build());
    }

    public static TrustLevel remoteToLocal(ContactRecord.IdentityState identityState) {
        return switch (identityState) {
            case DEFAULT -> TrustLevel.TRUSTED_UNVERIFIED;
            case UNVERIFIED -> TrustLevel.UNTRUSTED;
            case VERIFIED -> TrustLevel.TRUSTED_VERIFIED;
        };
    }

    private static IdentityState localToRemote(TrustLevel local) {
        return switch (local) {
            case TRUSTED_VERIFIED -> IdentityState.VERIFIED;
            case UNTRUSTED -> IdentityState.UNVERIFIED;
            default -> IdentityState.DEFAULT;
        };
    }
}
