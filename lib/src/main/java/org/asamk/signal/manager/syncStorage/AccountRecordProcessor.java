package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.internal.JobExecutor;
import org.asamk.signal.manager.jobs.CheckWhoAmIJob;
import org.asamk.signal.manager.jobs.DownloadProfileAvatarJob;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

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
            SignalAccount account, Connection connection, final JobExecutor jobExecutor
    ) throws SQLException {
        this.account = account;
        this.connection = connection;
        this.jobExecutor = jobExecutor;
        final var selfRecipientId = account.getSelfRecipientId();
        final var recipient = account.getRecipientStore().getRecipient(connection, selfRecipientId);
        final var storageId = account.getRecipientStore().getSelfStorageId(connection);
        this.localAccountRecord = StorageSyncModels.localToRemoteRecord(account.getConfigurationStore(),
                recipient,
                account.getUsernameLink(),
                storageId.getRaw()).getAccount().get();
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
    protected SignalAccountRecord merge(SignalAccountRecord remote, SignalAccountRecord local) {
        String givenName;
        String familyName;
        if (remote.getGivenName().isPresent() || remote.getFamilyName().isPresent()) {
            givenName = remote.getGivenName().orElse("");
            familyName = remote.getFamilyName().orElse("");
        } else {
            givenName = local.getGivenName().orElse("");
            familyName = local.getFamilyName().orElse("");
        }

        final var payments = remote.getPayments().getEntropy().isPresent() ? remote.getPayments() : local.getPayments();
        final var subscriber = remote.getSubscriber().getId().isPresent()
                ? remote.getSubscriber()
                : local.getSubscriber();
        final var storyViewReceiptsState = remote.getStoryViewReceiptsState() == OptionalBool.UNSET
                ? local.getStoryViewReceiptsState()
                : remote.getStoryViewReceiptsState();
        final var unknownFields = remote.serializeUnknownFields();
        final var avatarUrlPath = OptionalUtil.or(remote.getAvatarUrlPath(), local.getAvatarUrlPath()).orElse("");
        final var profileKey = OptionalUtil.or(remote.getProfileKey(), local.getProfileKey()).orElse(null);
        final var noteToSelfArchived = remote.isNoteToSelfArchived();
        final var noteToSelfForcedUnread = remote.isNoteToSelfForcedUnread();
        final var readReceipts = remote.isReadReceiptsEnabled();
        final var typingIndicators = remote.isTypingIndicatorsEnabled();
        final var sealedSenderIndicators = remote.isSealedSenderIndicatorsEnabled();
        final var linkPreviews = remote.isLinkPreviewsEnabled();
        final var unlisted = remote.isPhoneNumberUnlisted();
        final var pinnedConversations = remote.getPinnedConversations();
        final var phoneNumberSharingMode = remote.getPhoneNumberSharingMode();
        final var preferContactAvatars = remote.isPreferContactAvatars();
        final var universalExpireTimer = remote.getUniversalExpireTimer();
        final var e164 = account.isPrimaryDevice() ? local.getE164() : remote.getE164();
        final var defaultReactions = !remote.getDefaultReactions().isEmpty()
                ? remote.getDefaultReactions()
                : local.getDefaultReactions();
        final var displayBadgesOnProfile = remote.isDisplayBadgesOnProfile();
        final var subscriptionManuallyCancelled = remote.isSubscriptionManuallyCancelled();
        final var keepMutedChatsArchived = remote.isKeepMutedChatsArchived();
        final var hasSetMyStoriesPrivacy = remote.hasSetMyStoriesPrivacy();
        final var hasViewedOnboardingStory = remote.hasViewedOnboardingStory() || local.hasViewedOnboardingStory();
        final var storiesDisabled = remote.isStoriesDisabled();
        final var hasSeenGroupStoryEducation = remote.hasSeenGroupStoryEducationSheet()
                || local.hasSeenGroupStoryEducationSheet();
        boolean hasSeenUsernameOnboarding = remote.hasCompletedUsernameOnboarding()
                || local.hasCompletedUsernameOnboarding();
        final var username = remote.getUsername();
        final var usernameLink = remote.getUsernameLink();

        final var mergedBuilder = new SignalAccountRecord.Builder(remote.getId().getRaw(), unknownFields).setGivenName(
                        givenName)
                .setFamilyName(familyName)
                .setAvatarUrlPath(avatarUrlPath)
                .setProfileKey(profileKey)
                .setNoteToSelfArchived(noteToSelfArchived)
                .setNoteToSelfForcedUnread(noteToSelfForcedUnread)
                .setReadReceiptsEnabled(readReceipts)
                .setTypingIndicatorsEnabled(typingIndicators)
                .setSealedSenderIndicatorsEnabled(sealedSenderIndicators)
                .setLinkPreviewsEnabled(linkPreviews)
                .setUnlistedPhoneNumber(unlisted)
                .setPhoneNumberSharingMode(phoneNumberSharingMode)
                .setPinnedConversations(pinnedConversations)
                .setPreferContactAvatars(preferContactAvatars)
                .setPayments(payments.isEnabled(), payments.getEntropy().orElse(null))
                .setUniversalExpireTimer(universalExpireTimer)
                .setDefaultReactions(defaultReactions)
                .setSubscriber(subscriber)
                .setDisplayBadgesOnProfile(displayBadgesOnProfile)
                .setSubscriptionManuallyCancelled(subscriptionManuallyCancelled)
                .setKeepMutedChatsArchived(keepMutedChatsArchived)
                .setHasSetMyStoriesPrivacy(hasSetMyStoriesPrivacy)
                .setHasViewedOnboardingStory(hasViewedOnboardingStory)
                .setStoriesDisabled(storiesDisabled)
                .setHasSeenGroupStoryEducationSheet(hasSeenGroupStoryEducation)
                .setHasCompletedUsernameOnboarding(hasSeenUsernameOnboarding)
                .setStoryViewReceiptsState(storyViewReceiptsState)
                .setUsername(username)
                .setUsernameLink(usernameLink)
                .setE164(e164);
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
    protected void insertLocal(SignalAccountRecord record) {
        throw new UnsupportedOperationException(
                "We should always have a local AccountRecord, so we should never been inserting a new one.");
    }

    @Override
    protected void updateLocal(StorageRecordUpdate<SignalAccountRecord> update) throws SQLException {
        final var accountRecord = update.newRecord();

        if (!accountRecord.getE164().equals(account.getNumber())) {
            jobExecutor.enqueueJob(new CheckWhoAmIJob());
        }

        account.getConfigurationStore().setReadReceipts(connection, accountRecord.isReadReceiptsEnabled());
        account.getConfigurationStore().setTypingIndicators(connection, accountRecord.isTypingIndicatorsEnabled());
        account.getConfigurationStore()
                .setUnidentifiedDeliveryIndicators(connection, accountRecord.isSealedSenderIndicatorsEnabled());
        account.getConfigurationStore().setLinkPreviews(connection, accountRecord.isLinkPreviewsEnabled());
        account.getConfigurationStore()
                .setPhoneNumberSharingMode(connection,
                        StorageSyncModels.remoteToLocal(accountRecord.getPhoneNumberSharingMode()));
        account.getConfigurationStore().setPhoneNumberUnlisted(connection, accountRecord.isPhoneNumberUnlisted());

        account.setUsername(accountRecord.getUsername() != null && !accountRecord.getUsername().isEmpty()
                ? accountRecord.getUsername()
                : null);
        if (accountRecord.getUsernameLink() != null) {
            final var usernameLink = accountRecord.getUsernameLink();
            account.setUsernameLink(new UsernameLinkComponents(usernameLink.entropy.toByteArray(),
                    UuidUtil.parseOrThrow(usernameLink.serverId.toByteArray())));
            account.getConfigurationStore().setUsernameLinkColor(connection, usernameLink.color.name());
        }

        if (accountRecord.getProfileKey().isPresent()) {
            ProfileKey profileKey;
            try {
                profileKey = new ProfileKey(accountRecord.getProfileKey().get());
            } catch (InvalidInputException e) {
                logger.debug("Received invalid profile key from storage");
                profileKey = null;
            }
            if (profileKey != null) {
                account.setProfileKey(profileKey);
                final var avatarPath = accountRecord.getAvatarUrlPath().orElse(null);
                jobExecutor.enqueueJob(new DownloadProfileAvatarJob(avatarPath));
            }
        }

        final var profile = account.getRecipientStore().getProfile(connection, account.getSelfRecipientId());
        final var builder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
        builder.withGivenName(accountRecord.getGivenName().orElse(null));
        builder.withFamilyName(accountRecord.getFamilyName().orElse(null));
        account.getRecipientStore().storeProfile(connection, account.getSelfRecipientId(), builder.build());
        account.getRecipientStore()
                .storeStorageRecord(connection,
                        account.getSelfRecipientId(),
                        accountRecord.getId(),
                        accountRecord.toProto().encode());
    }

    @Override
    public int compare(SignalAccountRecord lhs, SignalAccountRecord rhs) {
        return 0;
    }

    private static boolean doProtosMatch(SignalAccountRecord merged, SignalAccountRecord other) {
        return Arrays.equals(merged.toProto().encode(), other.toProto().encode());
    }
}
