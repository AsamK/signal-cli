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
import org.whispersystems.signalservice.internal.util.concurrent.CascadingFuture;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProfileHelper {

    private final ProfileKeyProvider profileKeyProvider;

    private final UnidentifiedAccessProvider unidentifiedAccessProvider;

    private final MessagePipeProvider messagePipeProvider;

    private final MessageReceiverProvider messageReceiverProvider;

    private final SignalServiceAddressResolver addressResolver;

    public ProfileHelper(
            final ProfileKeyProvider profileKeyProvider,
            final UnidentifiedAccessProvider unidentifiedAccessProvider,
            final MessagePipeProvider messagePipeProvider,
            final MessageReceiverProvider messageReceiverProvider,
            final SignalServiceAddressResolver addressResolver
    ) {
        this.profileKeyProvider = profileKeyProvider;
        this.unidentifiedAccessProvider = unidentifiedAccessProvider;
        this.messagePipeProvider = messagePipeProvider;
        this.messageReceiverProvider = messageReceiverProvider;
        this.addressResolver = addressResolver;
    }

    public ProfileAndCredential retrieveProfileSync(
            RecipientId recipientId, SignalServiceProfile.RequestType requestType
    ) throws IOException {
        try {
            return retrieveProfile(recipientId, requestType).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PushNetworkException) {
                throw (PushNetworkException) e.getCause();
            } else if (e.getCause() instanceof NotFoundException) {
                throw (NotFoundException) e.getCause();
            } else {
                throw new IOException(e);
            }
        } catch (InterruptedException | TimeoutException e) {
            throw new PushNetworkException(e);
        }
    }

    public ListenableFuture<ProfileAndCredential> retrieveProfile(
            RecipientId recipientId, SignalServiceProfile.RequestType requestType
    ) {
        var unidentifiedAccess = getUnidentifiedAccess(recipientId);
        var profileKey = Optional.fromNullable(profileKeyProvider.getProfileKey(recipientId));

        final var address = addressResolver.resolveSignalServiceAddress(recipientId);
        if (unidentifiedAccess.isPresent()) {
            return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address,
                    profileKey,
                    unidentifiedAccess,
                    requestType),
                    () -> getSocketRetrievalFuture(address, profileKey, unidentifiedAccess, requestType),
                    () -> getPipeRetrievalFuture(address, profileKey, Optional.absent(), requestType),
                    () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                    e -> !(e instanceof NotFoundException));
        } else {
            return new CascadingFuture<>(Arrays.asList(() -> getPipeRetrievalFuture(address,
                    profileKey,
                    Optional.absent(),
                    requestType), () -> getSocketRetrievalFuture(address, profileKey, Optional.absent(), requestType)),
                    e -> !(e instanceof NotFoundException));
        }
    }

    private ListenableFuture<ProfileAndCredential> getPipeRetrievalFuture(
            SignalServiceAddress address,
            Optional<ProfileKey> profileKey,
            Optional<UnidentifiedAccess> unidentifiedAccess,
            SignalServiceProfile.RequestType requestType
    ) throws IOException {
        var unidentifiedPipe = messagePipeProvider.getMessagePipe(true);
        var pipe = unidentifiedPipe != null && unidentifiedAccess.isPresent()
                ? unidentifiedPipe
                : messagePipeProvider.getMessagePipe(false);
        if (pipe != null) {
            try {
                return pipe.getProfile(address, profileKey, unidentifiedAccess, requestType);
            } catch (NoClassDefFoundError e) {
                // Native zkgroup lib not available for ProfileKey
                if (!address.getNumber().isPresent()) {
                    throw new NotFoundException("Can't request profile without number");
                }
                var addressWithoutUuid = new SignalServiceAddress(Optional.absent(), address.getNumber());
                return pipe.getProfile(addressWithoutUuid, profileKey, unidentifiedAccess, requestType);
            }
        }

        throw new IOException("No pipe available!");
    }

    private ListenableFuture<ProfileAndCredential> getSocketRetrievalFuture(
            SignalServiceAddress address,
            Optional<ProfileKey> profileKey,
            Optional<UnidentifiedAccess> unidentifiedAccess,
            SignalServiceProfile.RequestType requestType
    ) throws NotFoundException {
        var receiver = messageReceiverProvider.getMessageReceiver();
        try {
            return receiver.retrieveProfile(address, profileKey, unidentifiedAccess, requestType);
        } catch (NoClassDefFoundError e) {
            // Native zkgroup lib not available for ProfileKey
            if (!address.getNumber().isPresent()) {
                throw new NotFoundException("Can't request profile without number");
            }
            var addressWithoutUuid = new SignalServiceAddress(Optional.absent(), address.getNumber());
            return receiver.retrieveProfile(addressWithoutUuid, profileKey, unidentifiedAccess, requestType);
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
