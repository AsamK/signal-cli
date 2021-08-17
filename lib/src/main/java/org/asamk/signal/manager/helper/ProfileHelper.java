package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;

import io.reactivex.rxjava3.core.Single;

public final class ProfileHelper {

    private final ProfileKeyProvider profileKeyProvider;

    private final UnidentifiedAccessProvider unidentifiedAccessProvider;

    private final ProfileServiceProvider profileServiceProvider;

    private final MessageReceiverProvider messageReceiverProvider;

    private final SignalServiceAddressResolver addressResolver;

    public ProfileHelper(
            final ProfileKeyProvider profileKeyProvider,
            final UnidentifiedAccessProvider unidentifiedAccessProvider,
            final ProfileServiceProvider profileServiceProvider,
            final MessageReceiverProvider messageReceiverProvider,
            final SignalServiceAddressResolver addressResolver
    ) {
        this.profileKeyProvider = profileKeyProvider;
        this.unidentifiedAccessProvider = unidentifiedAccessProvider;
        this.profileServiceProvider = profileServiceProvider;
        this.messageReceiverProvider = messageReceiverProvider;
        this.addressResolver = addressResolver;
    }

    public ProfileAndCredential retrieveProfileSync(
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

    public SignalServiceProfile retrieveProfileSync(String username) throws IOException {
        return messageReceiverProvider.getMessageReceiver().retrieveProfileByUsername(username, Optional.absent());
    }

    public Single<ProfileAndCredential> retrieveProfile(
            RecipientId recipientId, SignalServiceProfile.RequestType requestType
    ) throws IOException {
        var unidentifiedAccess = getUnidentifiedAccess(recipientId);
        var profileKey = Optional.fromNullable(profileKeyProvider.getProfileKey(recipientId));

        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        return retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
    }

    private Single<ProfileAndCredential> retrieveProfile(
            SignalServiceAddress address,
            Optional<ProfileKey> profileKey,
            Optional<UnidentifiedAccess> unidentifiedAccess,
            SignalServiceProfile.RequestType requestType
    ) throws IOException {
        var profileService = profileServiceProvider.getProfileService();

        Single<ServiceResponse<ProfileAndCredential>> responseSingle;
        try {
            responseSingle = profileService.getProfile(address, profileKey, unidentifiedAccess, requestType);
        } catch (NoClassDefFoundError e) {
            // Native zkgroup lib not available for ProfileKey
            if (!address.getNumber().isPresent()) {
                throw new NotFoundException("Can't request profile without number");
            }
            var addressWithoutUuid = new SignalServiceAddress(Optional.absent(), address.getNumber());
            responseSingle = profileService.getProfile(addressWithoutUuid, profileKey, unidentifiedAccess, requestType);
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

    private Optional<UnidentifiedAccess> getUnidentifiedAccess(RecipientId recipientId) {
        var unidentifiedAccess = unidentifiedAccessProvider.getAccessFor(recipientId);

        if (unidentifiedAccess.isPresent()) {
            return unidentifiedAccess.get().getTargetUnidentifiedAccess();
        }

        return Optional.absent();
    }
}
