package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.api.Profile;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.jetbrains.annotations.Nullable;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.manager.util.Utils.handleResponseException;

public class UnidentifiedAccessHelper {

    private static final Logger logger = LoggerFactory.getLogger(UnidentifiedAccessHelper.class);
    private static final long CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1);
    private static final byte[] UNRESTRICTED_KEY = new byte[16];

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    private SenderCertificate privacySenderCertificate;
    private SenderCertificate senderCertificate;

    public UnidentifiedAccessHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public void rotateSenderCertificates() {
        privacySenderCertificate = null;
        senderCertificate = null;
    }

    public List<SealedSenderAccess> getSealedSenderAccessFor(List<RecipientId> recipients) {
        return recipients.stream().map(this::getAccessFor).map(SealedSenderAccess::forIndividual).toList();
    }

    public @Nullable SealedSenderAccess getSealedSenderAccessFor(RecipientId recipient) {
        return getSealedSenderAccessFor(recipient, false);
    }

    public @Nullable SealedSenderAccess getSealedSenderAccessFor(RecipientId recipient, boolean noRefresh) {
        return SealedSenderAccess.forIndividual(getAccessFor(recipient, noRefresh));
    }

    public List<UnidentifiedAccess> getAccessFor(List<RecipientId> recipients) {
        return recipients.stream().map(this::getAccessFor).toList();
    }

    private @Nullable UnidentifiedAccess getAccessFor(RecipientId recipient) {
        return getAccessFor(recipient, false);
    }

    private @Nullable UnidentifiedAccess getAccessFor(RecipientId recipientId, boolean noRefresh) {
        var recipientUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipientId, noRefresh);
        if (recipientUnidentifiedAccessKey == null) {
            logger.trace("Unidentified access not available for {}", recipientId);
            return null;
        }

        var selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey(noRefresh);
        if (selfUnidentifiedAccessKey == null) {
            logger.trace("Unidentified access not available for self");
            return null;
        }

        var senderCertificate = getSenderCertificateFor(recipientId);
        if (senderCertificate == null) {
            logger.trace("Unidentified access not available due to missing sender certificate");
            return null;
        }

        try {
            return new UnidentifiedAccess(recipientUnidentifiedAccessKey, senderCertificate, false);
        } catch (InvalidCertificateException e) {
            return null;
        }
    }

    private byte[] getSenderCertificateFor(final RecipientId recipientId) {
        final var sharingMode = account.getConfigurationStore().getPhoneNumberSharingMode();
        if (sharingMode == PhoneNumberSharingMode.EVERYBODY || (
                sharingMode == PhoneNumberSharingMode.CONTACTS
                        && account.getContactStore().getContact(recipientId) != null
        )) {
            logger.trace("Using normal sender certificate for message to {}", recipientId);
            return getSenderCertificate();
        } else {
            logger.trace("Using phone number privacy sender certificate for message to {}", recipientId);
            return getSenderCertificateForPhoneNumberPrivacy();
        }
    }

    private byte[] getSenderCertificateForPhoneNumberPrivacy() {
        if (privacySenderCertificate != null && System.currentTimeMillis() < (
                privacySenderCertificate.getExpiration() - CERTIFICATE_EXPIRATION_BUFFER
        )) {
            return privacySenderCertificate.getSerialized();
        }
        try {
            final var certificate = handleResponseException(dependencies.getCertificateApi()
                    .getSenderCertificateForPhoneNumberPrivacy());
            privacySenderCertificate = new SenderCertificate(certificate);
            return certificate;
        } catch (IOException | InvalidCertificateException e) {
            logger.warn("Failed to get sender certificate (pnp), ignoring: {}", e.getMessage());
            return null;
        }
    }

    private byte[] getSenderCertificate() {
        if (senderCertificate != null && System.currentTimeMillis() < (
                senderCertificate.getExpiration() - CERTIFICATE_EXPIRATION_BUFFER
        )) {
            return senderCertificate.getSerialized();
        }
        try {
            final var certificate = handleResponseException(dependencies.getCertificateApi().getSenderCertificate());
            this.senderCertificate = new SenderCertificate(certificate);
            return certificate;
        } catch (IOException | InvalidCertificateException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
    }

    private byte[] getSelfUnidentifiedAccessKey(boolean noRefresh) {
        var selfProfile = noRefresh
                ? account.getProfileStore().getProfile(account.getSelfRecipientId())
                : context.getProfileHelper().getSelfProfile();
        if (selfProfile != null
                && selfProfile.getUnidentifiedAccessMode() == Profile.UnidentifiedAccessMode.UNRESTRICTED) {
            return createUnrestrictedUnidentifiedAccess();
        }
        return UnidentifiedAccess.deriveAccessKeyFrom(account.getProfileKey());
    }

    private byte[] getTargetUnidentifiedAccessKey(RecipientId recipientId, boolean noRefresh) {
        var targetProfile = noRefresh
                ? account.getProfileStore().getProfile(recipientId)
                : context.getProfileHelper().getRecipientProfile(recipientId);
        if (targetProfile == null) {
            return null;
        }

        var theirProfileKey = account.getProfileStore().getProfileKey(recipientId);
        return getTargetUnidentifiedAccessKey(targetProfile, theirProfileKey);
    }

    private static byte[] getTargetUnidentifiedAccessKey(
            final Profile targetProfile,
            final ProfileKey theirProfileKey
    ) {
        return switch (targetProfile.getUnidentifiedAccessMode()) {
            case ENABLED -> theirProfileKey == null ? null : UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
            case UNRESTRICTED -> createUnrestrictedUnidentifiedAccess();
            default -> null;
        };
    }

    private static byte[] createUnrestrictedUnidentifiedAccess() {
        return UNRESTRICTED_KEY;
    }
}
