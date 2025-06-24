package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.internal.JobExecutor;
import org.asamk.signal.manager.jobs.DownloadProfileAvatarJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

import okio.ByteString;

import static org.asamk.signal.manager.util.Utils.firstNonEmpty;
import static org.whispersystems.signalservice.api.storage.AccountRecordExtensionsKt.safeSetBackupsSubscriber;
import static org.whispersystems.signalservice.api.storage.AccountRecordExtensionsKt.safeSetPayments;
import static org.whispersystems.signalservice.api.storage.AccountRecordExtensionsKt.safeSetSubscriber;

/**
 * Processes {@link SignalAccountRecord}s.
 */
public class AccountRecordProcessor extends DefaultStorageRecordProcessor<SignalAccountRecord> {

    private static final Logger logger = LoggerFactory.getLogger(AccountRecordProcessor.class);
    private final SignalAccountRecord localAccountRecord;
    private final SignalAccount account;
    private final Connection connection;
    private final JobExecutor jobExecutor;

    public AccountRecordProcessor(
            SignalAccount account,
            Connection connection,
            final JobExecutor jobExecutor
    ) throws SQLException {
        this.account = account;
        this.connection = connection;
        this.jobExecutor = jobExecutor;
        final var selfRecipientId = account.getSelfRecipientId();
        final var recipient = account.getRecipientStore().getRecipient(connection, selfRecipientId);
        final var storageId = account.getRecipientStore().getSelfStorageId(connection);
        this.localAccountRecord = new SignalAccountRecord(storageId,
                StorageSyncModels.localToRemoteRecord(connection,
                        account.getConfigurationStore(),
                        recipient,
                        account.getUsernameLink()));
    }

    @Override
    protected boolean isInvalid(SignalAccountRecord remote) {
        return false;
    }

    @Override
    protected Optional<SignalAccountRecord> getMatching(SignalAccountRecord record) {
        return Optional.of(localAccountRecord);
    }

    @Override
    protected SignalAccountRecord merge(SignalAccountRecord remoteRecord, SignalAccountRecord localRecord) {
        final var remote = remoteRecord.getProto();
        final var local = localRecord.getProto();
        String givenName;
        String familyName;
        if (!remote.givenName.isEmpty() || !remote.familyName.isEmpty()) {
            givenName = remote.givenName;
            familyName = remote.familyName;
        } else {
            givenName = local.givenName;
            familyName = local.familyName;
        }

        final var payments = remote.payments != null && remote.payments.entropy.size() > 0
                ? remote.payments
                : local.payments;

        final ByteString donationSubscriberId;
        final String donationSubscriberCurrencyCode;

        if (remote.subscriberId.size() > 0) {
            donationSubscriberId = remote.subscriberId;
            donationSubscriberCurrencyCode = remote.subscriberCurrencyCode;
        } else {
            donationSubscriberId = local.subscriberId;
            donationSubscriberCurrencyCode = local.subscriberCurrencyCode;
        }

        final ByteString backupsSubscriberId;
        final IAPSubscriptionId backupsPurchaseToken;

        final var remoteBackupSubscriberData = remote.backupSubscriberData;
        if (remoteBackupSubscriberData != null && remoteBackupSubscriberData.subscriberId.size() > 0) {
            backupsSubscriberId = remoteBackupSubscriberData.subscriberId;
            backupsPurchaseToken = IAPSubscriptionId.Companion.from(remoteBackupSubscriberData);
        } else {
            backupsSubscriberId = local.backupSubscriberData != null
                    ? local.backupSubscriberData.subscriberId
                    : ByteString.EMPTY;
            backupsPurchaseToken = IAPSubscriptionId.Companion.from(local.backupSubscriberData);
        }

        final var mergedBuilder = remote.newBuilder()
                .givenName(givenName)
                .familyName(familyName)
                .avatarUrlPath(firstNonEmpty(remote.avatarUrlPath, local.avatarUrlPath))
                .profileKey(firstNonEmpty(remote.profileKey, local.profileKey))
                .noteToSelfArchived(remote.noteToSelfArchived)
                .noteToSelfMarkedUnread(remote.noteToSelfMarkedUnread)
                .readReceipts(remote.readReceipts)
                .typingIndicators(remote.typingIndicators)
                .sealedSenderIndicators(remote.sealedSenderIndicators)
                .linkPreviews(remote.linkPreviews)
                .unlistedPhoneNumber(remote.unlistedPhoneNumber)
                .phoneNumberSharingMode(remote.phoneNumberSharingMode)
                .pinnedConversations(remote.pinnedConversations)
                .preferContactAvatars(remote.preferContactAvatars)
                .universalExpireTimer(remote.universalExpireTimer)
                .preferredReactionEmoji(firstNonEmpty(remote.preferredReactionEmoji, local.preferredReactionEmoji))
                .subscriberId(firstNonEmpty(remote.subscriberId, local.subscriberId))
                .subscriberCurrencyCode(firstNonEmpty(remote.subscriberCurrencyCode, local.subscriberCurrencyCode))
                .displayBadgesOnProfile(remote.displayBadgesOnProfile)
                .subscriptionManuallyCancelled(remote.subscriptionManuallyCancelled)
                .keepMutedChatsArchived(remote.keepMutedChatsArchived)
                .hasSetMyStoriesPrivacy(remote.hasSetMyStoriesPrivacy)
                .hasViewedOnboardingStory(remote.hasViewedOnboardingStory || local.hasViewedOnboardingStory)
                .storiesDisabled(remote.storiesDisabled)
                .hasSeenGroupStoryEducationSheet(remote.hasSeenGroupStoryEducationSheet
                        || local.hasSeenGroupStoryEducationSheet)
                .hasCompletedUsernameOnboarding(remote.hasCompletedUsernameOnboarding
                        || local.hasCompletedUsernameOnboarding)
                .storyViewReceiptsEnabled(remote.storyViewReceiptsEnabled == OptionalBool.UNSET
                        ? local.storyViewReceiptsEnabled
                        : remote.storyViewReceiptsEnabled)
                .username(remote.username)
                .usernameLink(remote.usernameLink)
                .avatarColor(remote.avatarColor);
        safeSetPayments(mergedBuilder,
                payments != null && payments.enabled,
                payments == null ? null : payments.entropy.toByteArray());
        safeSetSubscriber(mergedBuilder, donationSubscriberId, donationSubscriberCurrencyCode);
        safeSetBackupsSubscriber(mergedBuilder, backupsSubscriberId, backupsPurchaseToken);

        final var merged = mergedBuilder.build();

        final var matchesRemote = doProtosMatch(merged, remote);
        if (matchesRemote) {
            return remoteRecord;
        }

        final var matchesLocal = doProtosMatch(merged, local);
        if (matchesLocal) {
            return localRecord;
        }

        return new SignalAccountRecord(StorageId.forAccount(KeyUtils.createRawStorageId()), mergedBuilder.build());
    }

    @Override
    protected void insertLocal(SignalAccountRecord record) {
        throw new UnsupportedOperationException(
                "We should always have a local AccountRecord, so we should never been inserting a new one.");
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalAccountRecord> update) throws SQLException {
        final var accountRecord = update.newRecord();
        final var accountProto = accountRecord.getProto();

        account.getConfigurationStore().setReadReceipts(connection, accountProto.readReceipts);
        account.getConfigurationStore().setTypingIndicators(connection, accountProto.typingIndicators);
        account.getConfigurationStore()
                .setUnidentifiedDeliveryIndicators(connection, accountProto.sealedSenderIndicators);
        account.getConfigurationStore().setLinkPreviews(connection, accountProto.linkPreviews);
        account.getConfigurationStore()
                .setPhoneNumberSharingMode(connection,
                        StorageSyncModels.remoteToLocal(accountProto.phoneNumberSharingMode));
        account.getConfigurationStore().setPhoneNumberUnlisted(connection, accountProto.unlistedPhoneNumber);

        account.setUsername(!accountProto.username.isEmpty() ? accountProto.username : null);
        if (accountProto.usernameLink != null) {
            final var usernameLink = accountProto.usernameLink;
            account.setUsernameLink(new UsernameLinkComponents(usernameLink.entropy.toByteArray(),
                    UuidUtil.parseOrThrow(usernameLink.serverId.toByteArray())));
            account.getConfigurationStore().setUsernameLinkColor(connection, usernameLink.color.name());
        }

        if (accountProto.profileKey.size() > 0) {
            ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(accountProto.profileKey.toByteArray());
            } catch (InvalidInputException e) {
                logger.debug("Received invalid profile key from storage");
                profileKey = null;
            }
            if (profileKey != null) {
                account.setProfileKey(profileKey);
                final var avatarPath = accountProto.avatarUrlPath.isEmpty() ? null : accountProto.avatarUrlPath;
                jobExecutor.enqueueJob(new DownloadProfileAvatarJob(avatarPath));
            }
        }

        final var profile = account.getRecipientStore().getProfile(connection, account.getSelfRecipientId());
        final var builder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
        builder.withGivenName(accountProto.givenName);
        builder.withFamilyName(accountProto.familyName);
        account.getRecipientStore().storeProfile(connection, account.getSelfRecipientId(), builder.build());
        account.getRecipientStore()
                .storeStorageRecord(connection,
                        account.getSelfRecipientId(),
                        accountRecord.getId(),
                        accountProto.encode());
    }

    @Override
    public int compare(SignalAccountRecord lhs, SignalAccountRecord rhs) {
        return 0;
    }

    private static boolean doProtosMatch(AccountRecord merged, AccountRecord other) {
        return Arrays.equals(merged.encode(), other.encode());
    }
}
