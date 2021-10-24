package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.Profile;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.ProfileUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;

public final class ProfileHelper {

    private final static Logger logger = LoggerFactory.getLogger(ProfileHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final AvatarStore avatarStore;
    private final UnidentifiedAccessProvider unidentifiedAccessProvider;
    private final SignalServiceAddressResolver addressResolver;

    public ProfileHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final AvatarStore avatarStore,
            final UnidentifiedAccessProvider unidentifiedAccessProvider,
            final SignalServiceAddressResolver addressResolver
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.avatarStore = avatarStore;
        this.unidentifiedAccessProvider = unidentifiedAccessProvider;
        this.addressResolver = addressResolver;
    }

    public Profile getRecipientProfile(RecipientId recipientId) {
        return getRecipientProfile(recipientId, false);
    }

    public void refreshRecipientProfile(RecipientId recipientId) {
        getRecipientProfile(recipientId, true);
    }

    public ProfileKeyCredential getRecipientProfileKeyCredential(RecipientId recipientId) {
        var profileKeyCredential = account.getProfileStore().getProfileKeyCredential(recipientId);
        if (profileKeyCredential != null) {
            return profileKeyCredential;
        }

        ProfileAndCredential profileAndCredential;
        try {
            profileAndCredential = retrieveProfileAndCredential(recipientId,
                    SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL);
        } catch (IOException e) {
            logger.warn("Failed to retrieve profile key credential, ignoring: {}", e.getMessage());
            return null;
        }

        profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
        account.getProfileStore().storeProfileKeyCredential(recipientId, profileKeyCredential);

        var profileKey = account.getProfileStore().getProfileKey(recipientId);
        if (profileKey != null) {
            final var profile = decryptProfileAndDownloadAvatar(recipientId,
                    profileKey,
                    profileAndCredential.getProfile());
            account.getProfileStore().storeProfile(recipientId, profile);
        }

        return profileKeyCredential;
    }

    /**
     * @param givenName  if null, the previous givenName will be kept
     * @param familyName if null, the previous familyName will be kept
     * @param about      if null, the previous about text will be kept
     * @param aboutEmoji if null, the previous about emoji will be kept
     * @param avatar     if avatar is null the image from the local avatar store is used (if present),
     */
    public void setProfile(
            String givenName, final String familyName, String about, String aboutEmoji, Optional<File> avatar
    ) throws IOException {
        setProfile(true, givenName, familyName, about, aboutEmoji, avatar);
    }

    public void setProfile(
            boolean uploadProfile,
            String givenName,
            final String familyName,
            String about,
            String aboutEmoji,
            Optional<File> avatar
    ) throws IOException {
        var profile = getRecipientProfile(account.getSelfRecipientId());
        var builder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
        if (givenName != null) {
            builder.withGivenName(givenName);
        }
        if (familyName != null) {
            builder.withFamilyName(familyName);
        }
        if (about != null) {
            builder.withAbout(about);
        }
        if (aboutEmoji != null) {
            builder.withAboutEmoji(aboutEmoji);
        }
        var newProfile = builder.build();

        if (uploadProfile) {
            try (final var streamDetails = avatar == null
                    ? avatarStore.retrieveProfileAvatar(account.getSelfAddress())
                    : avatar.isPresent() ? Utils.createStreamDetailsFromFile(avatar.get()) : null) {
                final var avatarPath = dependencies.getAccountManager()
                        .setVersionedProfile(account.getUuid(),
                                account.getProfileKey(),
                                newProfile.getInternalServiceName(),
                                newProfile.getAbout() == null ? "" : newProfile.getAbout(),
                                newProfile.getAboutEmoji() == null ? "" : newProfile.getAboutEmoji(),
                                Optional.absent(),
                                streamDetails,
                                List.of(/* TODO */));
                builder.withAvatarUrlPath(avatarPath.orNull());
                newProfile = builder.build();
            }
        }

        if (avatar != null) {
            if (avatar.isPresent()) {
                avatarStore.storeProfileAvatar(account.getSelfAddress(),
                        outputStream -> IOUtils.copyFileToStream(avatar.get(), outputStream));
            } else {
                avatarStore.deleteProfileAvatar(account.getSelfAddress());
            }
        }
        account.getProfileStore().storeProfile(account.getSelfRecipientId(), newProfile);
    }

    private final Set<RecipientId> pendingProfileRequest = new HashSet<>();

    private Profile getRecipientProfile(RecipientId recipientId, boolean force) {
        var profile = account.getProfileStore().getProfile(recipientId);

        var now = System.currentTimeMillis();
        // Profiles are cached for 24h before retrieving them again, unless forced
        if (!force && profile != null && now - profile.getLastUpdateTimestamp() < 24 * 60 * 60 * 1000) {
            return profile;
        }

        synchronized (pendingProfileRequest) {
            if (pendingProfileRequest.contains(recipientId)) {
                return profile;
            }
            pendingProfileRequest.add(recipientId);
        }
        final SignalServiceProfile encryptedProfile;
        try {
            encryptedProfile = retrieveEncryptedProfile(recipientId);
        } finally {
            synchronized (pendingProfileRequest) {
                pendingProfileRequest.remove(recipientId);
            }
        }
        if (encryptedProfile == null) {
            return null;
        }

        profile = decryptProfileIfKeyKnown(recipientId, encryptedProfile);
        account.getProfileStore().storeProfile(recipientId, profile);

        return profile;
    }

    private Profile decryptProfileIfKeyKnown(
            final RecipientId recipientId, final SignalServiceProfile encryptedProfile
    ) {
        var profileKey = account.getProfileStore().getProfileKey(recipientId);
        if (profileKey == null) {
            return new Profile(System.currentTimeMillis(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    ProfileUtils.getUnidentifiedAccessMode(encryptedProfile, null),
                    ProfileUtils.getCapabilities(encryptedProfile));
        }

        return decryptProfileAndDownloadAvatar(recipientId, profileKey, encryptedProfile);
    }

    private SignalServiceProfile retrieveEncryptedProfile(RecipientId recipientId) {
        try {
            return retrieveProfileAndCredential(recipientId, SignalServiceProfile.RequestType.PROFILE).getProfile();
        } catch (IOException e) {
            logger.warn("Failed to retrieve profile, ignoring: {}", e.getMessage());
            return null;
        }
    }

    private SignalServiceProfile retrieveProfileSync(String username) throws IOException {
        final var locale = Locale.getDefault();
        return dependencies.getMessageReceiver().retrieveProfileByUsername(username, Optional.absent(), locale);
    }

    private ProfileAndCredential retrieveProfileAndCredential(
            final RecipientId recipientId, final SignalServiceProfile.RequestType requestType
    ) throws IOException {
        final var profileAndCredential = retrieveProfileSync(recipientId, requestType);
        final var profile = profileAndCredential.getProfile();

        try {
            var newIdentity = account.getIdentityKeyStore()
                    .saveIdentity(recipientId,
                            new IdentityKey(Base64.getDecoder().decode(profile.getIdentityKey())),
                            new Date());

            if (newIdentity) {
                account.getSessionStore().archiveSessions(recipientId);
            }
        } catch (InvalidKeyException ignored) {
            logger.warn("Got invalid identity key in profile for {}",
                    addressResolver.resolveSignalServiceAddress(recipientId).getIdentifier());
        }
        return profileAndCredential;
    }

    private Profile decryptProfileAndDownloadAvatar(
            final RecipientId recipientId, final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        final var avatarPath = encryptedProfile.getAvatar();
        downloadProfileAvatar(recipientId, avatarPath, profileKey);

        return ProfileUtils.decryptProfile(profileKey, encryptedProfile);
    }

    public void downloadProfileAvatar(
            final RecipientId recipientId, final String avatarPath, final ProfileKey profileKey
    ) {
        var profile = account.getProfileStore().getProfile(recipientId);
        if (profile == null || !Objects.equals(avatarPath, profile.getAvatarUrlPath())) {
            downloadProfileAvatar(addressResolver.resolveSignalServiceAddress(recipientId), avatarPath, profileKey);
            var builder = profile == null ? Profile.newBuilder() : Profile.newBuilder(profile);
            account.getProfileStore().storeProfile(recipientId, builder.withAvatarUrlPath(avatarPath).build());
        }
    }

    private ProfileAndCredential retrieveProfileSync(
            RecipientId recipientId, SignalServiceProfile.RequestType requestType
    ) throws IOException {
        try {
            return retrieveProfile(recipientId, requestType).blockingGet();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof PushNetworkException) {
                throw (PushNetworkException) e.getCause();
            } else if (e.getCause() instanceof NotFoundException) {
                throw (NotFoundException) e.getCause();
            } else {
                throw new IOException(e);
            }
        }
    }

    private Single<ProfileAndCredential> retrieveProfile(
            RecipientId recipientId, SignalServiceProfile.RequestType requestType
    ) {
        var unidentifiedAccess = getUnidentifiedAccess(recipientId);
        var profileKey = Optional.fromNullable(account.getProfileStore().getProfileKey(recipientId));

        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        return retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
    }

    private Single<ProfileAndCredential> retrieveProfile(
            SignalServiceAddress address,
            Optional<ProfileKey> profileKey,
            Optional<UnidentifiedAccess> unidentifiedAccess,
            SignalServiceProfile.RequestType requestType
    ) {
        var profileService = dependencies.getProfileService();

        Single<ServiceResponse<ProfileAndCredential>> responseSingle;
        final var locale = Locale.getDefault();
        try {
            responseSingle = profileService.getProfile(address, profileKey, unidentifiedAccess, requestType, locale);
        } catch (NoClassDefFoundError e) {
            // Native zkgroup lib not available for ProfileKey
            responseSingle = profileService.getProfile(address,
                    Optional.absent(),
                    unidentifiedAccess,
                    requestType,
                    locale);
        }

        return responseSingle.map(pair -> {
            var processor = new ProfileService.ProfileResponseProcessor(pair);
            if (processor.hasResult()) {
                return processor.getResult();
            } else if (processor.notFound()) {
                throw new NotFoundException("Profile not found");
            } else {
                throw pair.getExecutionError()
                        .or(pair.getApplicationError())
                        .or(new IOException("Unknown error while retrieving profile"));
            }
        });
    }

    private void downloadProfileAvatar(
            SignalServiceAddress address, String avatarPath, ProfileKey profileKey
    ) {
        if (avatarPath == null) {
            try {
                avatarStore.deleteProfileAvatar(address);
            } catch (IOException e) {
                logger.warn("Failed to delete local profile avatar, ignoring: {}", e.getMessage());
            }
            return;
        }

        try {
            avatarStore.storeProfileAvatar(address,
                    outputStream -> retrieveProfileAvatar(avatarPath, profileKey, outputStream));
        } catch (Throwable e) {
            if (e instanceof AssertionError && e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to download profile avatar, ignoring: {}", e.getMessage());
        }
    }

    private void retrieveProfileAvatar(
            String avatarPath, ProfileKey profileKey, OutputStream outputStream
    ) throws IOException {
        var tmpFile = IOUtils.createTempFile();
        try (var input = dependencies.getMessageReceiver()
                .retrieveProfileAvatar(avatarPath,
                        tmpFile,
                        profileKey,
                        ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
            // Use larger buffer size to prevent AssertionError: Need: 12272 but only have: 8192 ...
            IOUtils.copyStream(input, outputStream, (int) ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received profile avatar temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private Optional<UnidentifiedAccess> getUnidentifiedAccess(RecipientId recipientId) {
        var unidentifiedAccess = unidentifiedAccessProvider.getAccessFor(recipientId);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }
}
