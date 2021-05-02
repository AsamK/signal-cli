package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

import java.util.List;
import java.util.stream.Collectors;

import static org.whispersystems.signalservice.internal.util.Util.getSecretBytes;

public class UnidentifiedAccessHelper {

    private final SelfProfileKeyProvider selfProfileKeyProvider;

    private final ProfileKeyProvider profileKeyProvider;

    private final ProfileProvider profileProvider;

    private final UnidentifiedAccessSenderCertificateProvider senderCertificateProvider;

    public UnidentifiedAccessHelper(
            final SelfProfileKeyProvider selfProfileKeyProvider,
            final ProfileKeyProvider profileKeyProvider,
            final ProfileProvider profileProvider,
            final UnidentifiedAccessSenderCertificateProvider senderCertificateProvider
    ) {
        this.selfProfileKeyProvider = selfProfileKeyProvider;
        this.profileKeyProvider = profileKeyProvider;
        this.profileProvider = profileProvider;
        this.senderCertificateProvider = senderCertificateProvider;
    }

    private byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(selfProfileKeyProvider.getProfileKey());
    }

    public byte[] getTargetUnidentifiedAccessKey(RecipientId recipient) {
        var targetProfile = profileProvider.getProfile(recipient);
        if (targetProfile == null) {
            return null;
        }

        switch (targetProfile.getUnidentifiedAccessMode()) {
            case ENABLED:
                var theirProfileKey = profileKeyProvider.getProfileKey(recipient);
                if (theirProfileKey == null) {
                    return null;
                }

                return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
            case UNRESTRICTED:
                return createUnrestrictedUnidentifiedAccess();
            default:
                return null;
        }
    }

    public Optional<UnidentifiedAccessPair> getAccessForSync() {
        var selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        var selfUnidentifiedAccessCertificate = senderCertificateProvider.getSenderCertificate();

        if (selfUnidentifiedAccessKey == null || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(selfUnidentifiedAccessKey,
                    selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    public List<Optional<UnidentifiedAccessPair>> getAccessFor(List<RecipientId> recipients) {
        return recipients.stream().map(this::getAccessFor).collect(Collectors.toList());
    }

    public Optional<UnidentifiedAccessPair> getAccessFor(RecipientId recipient) {
        var recipientUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipient);
        var selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        var selfUnidentifiedAccessCertificate = senderCertificateProvider.getSenderCertificate();

        if (recipientUnidentifiedAccessKey == null
                || selfUnidentifiedAccessKey == null
                || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(recipientUnidentifiedAccessKey,
                    selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    private static byte[] createUnrestrictedUnidentifiedAccess() {
        return getSecretBytes(16);
    }
}
