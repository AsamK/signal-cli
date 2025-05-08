package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.configuration.ConfigurationStore;
import org.asamk.signal.manager.storage.groups.GroupInfoV1;
import org.asamk.signal.manager.storage.groups.GroupInfoV2;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.manager.storage.recipients.Recipient;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord.UsernameLink;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import okio.ByteString;

import static org.signal.core.util.StringExtensionsKt.emptyIfNull;

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
            case NOBODY -> PhoneNumberSharingMode.NOBODY;
            case UNKNOWN -> null;
        };
    }

    public static AccountRecord localToRemoteRecord(
            final Connection connection,
            ConfigurationStore configStore,
            Recipient self,
            UsernameLinkComponents usernameLinkComponents
    ) throws SQLException {
        final var builder = SignalAccountRecord.Companion.newBuilder(self.getStorageRecord());
        if (self.getProfileKey() != null) {
            builder.profileKey(ByteString.of(self.getProfileKey().serialize()));
        }
        if (self.getProfile() != null) {
            builder.givenName(emptyIfNull(self.getProfile().getGivenName()))
                    .familyName(emptyIfNull(self.getProfile().getFamilyName()))
                    .avatarUrlPath(emptyIfNull(self.getProfile().getAvatarUrlPath()));
        }
        builder.typingIndicators(Optional.ofNullable(configStore.getTypingIndicators(connection)).orElse(true))
                .readReceipts(Optional.ofNullable(configStore.getReadReceipts(connection)).orElse(true))
                .sealedSenderIndicators(Optional.ofNullable(configStore.getUnidentifiedDeliveryIndicators(connection))
                        .orElse(true))
                .linkPreviews(Optional.ofNullable(configStore.getLinkPreviews(connection)).orElse(true))
                .unlistedPhoneNumber(Optional.ofNullable(configStore.getPhoneNumberUnlisted(connection)).orElse(false))
                .phoneNumberSharingMode(Optional.ofNullable(configStore.getPhoneNumberSharingMode(connection))
                        .map(StorageSyncModels::localToRemote)
                        .orElse(AccountRecord.PhoneNumberSharingMode.UNKNOWN))
                .username(self.getAddress().username().orElse(""));
        if (usernameLinkComponents != null) {
            final var linkColor = configStore.getUsernameLinkColor(connection);
            builder.usernameLink(new UsernameLink.Builder().entropy(ByteString.of(usernameLinkComponents.getEntropy()))
                    .serverId(UuidUtil.toByteString(usernameLinkComponents.getServerId()))
                    .color(linkColor == null ? UsernameLink.Color.UNKNOWN : UsernameLink.Color.valueOf(linkColor))
                    .build());
        }

        return builder.build();
    }

    public static ContactRecord localToRemoteRecord(Recipient recipient, IdentityInfo identity) {
        final var address = recipient.getAddress();
        final var builder = SignalContactRecord.Companion.newBuilder(recipient.getStorageRecord())
                .aci(address.aci().map(ACI::toString).orElse(""))
                .e164(address.number().orElse(""))
                .pni(address.pni().map(PNI::toStringWithoutPrefix).orElse(""))
                .username(address.username().orElse(""))
                .profileKey(recipient.getProfileKey() == null
                        ? ByteString.EMPTY
                        : ByteString.of(recipient.getProfileKey().serialize()));
        if (recipient.getProfile() != null) {
            builder.givenName(emptyIfNull(recipient.getProfile().getGivenName()))
                    .familyName(emptyIfNull(recipient.getProfile().getFamilyName()));
        }
        if (recipient.getContact() != null) {
            builder.systemGivenName(emptyIfNull(recipient.getContact().givenName()))
                    .systemFamilyName(emptyIfNull(recipient.getContact().familyName()))
                    .systemNickname(emptyIfNull(recipient.getContact().nickName()))
                    .nickname(new ContactRecord.Name.Builder().given(emptyIfNull(recipient.getContact()
                                    .nickNameGivenName()))
                            .family(emptyIfNull(recipient.getContact().nickNameFamilyName()))
                            .build())
                    .note(emptyIfNull(recipient.getContact().note()))
                    .blocked(recipient.getContact().isBlocked())
                    .whitelisted(recipient.getContact().isProfileSharingEnabled())
                    .mutedUntilTimestamp(recipient.getContact().muteUntil())
                    .hideStory(recipient.getContact().hideStory())
                    .unregisteredAtTimestamp(recipient.getContact().unregisteredTimestamp() == null
                            ? 0
                            : recipient.getContact().unregisteredTimestamp())
                    .archived(recipient.getContact().isArchived())
                    .hidden(recipient.getContact().isHidden());
        }
        if (identity != null) {
            builder.identityKey(ByteString.of(identity.getIdentityKey().serialize()))
                    .identityState(localToRemote(identity.getTrustLevel()));
        }
        return builder.build();
    }

    public static GroupV1Record localToRemoteRecord(GroupInfoV1 group) {
        final var builder = SignalGroupV1Record.Companion.newBuilder(group.getStorageRecord());
        builder.id(ByteString.of(group.getGroupId().serialize()));
        builder.blocked(group.isBlocked());
        builder.archived(group.archived);
        builder.whitelisted(true);
        return builder.build();
    }

    public static GroupV2Record localToRemoteRecord(GroupInfoV2 group) {
        final var builder = SignalGroupV2Record.Companion.newBuilder(group.getStorageRecord());
        builder.masterKey(ByteString.of(group.getMasterKey().serialize()));
        builder.blocked(group.isBlocked());
        builder.whitelisted(group.isProfileSharingEnabled());
        return builder.build();
    }

    public static TrustLevel remoteToLocal(IdentityState identityState) {
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
