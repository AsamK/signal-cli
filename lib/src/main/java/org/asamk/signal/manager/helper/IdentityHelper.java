package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.FingerprintParsingException;
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException;
import org.signal.libsignal.protocol.fingerprint.ScannableFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class IdentityHelper {

    private final static Logger logger = LoggerFactory.getLogger(IdentityHelper.class);

    private final SignalAccount account;
    private final Context context;

    public IdentityHelper(final Context context) {
        this.account = context.getAccount();
        this.context = context;
    }

    public boolean trustIdentityVerified(RecipientId recipientId, byte[] fingerprint) {
        final var serviceId = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId).getServiceId();
        return trustIdentity(serviceId,
                identityKey -> Arrays.equals(identityKey.serialize(), fingerprint),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, String safetyNumber) {
        final var serviceId = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId).getServiceId();
        return trustIdentity(serviceId,
                identityKey -> safetyNumber.equals(computeSafetyNumber(serviceId, identityKey)),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, byte[] safetyNumber) {
        final var serviceId = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId).getServiceId();
        return trustIdentity(serviceId, identityKey -> {
            final var fingerprint = computeSafetyNumberForScanning(serviceId, identityKey);
            try {
                return fingerprint != null && fingerprint.compareTo(safetyNumber);
            } catch (FingerprintVersionMismatchException | FingerprintParsingException e) {
                return false;
            }
        }, TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityAllKeys(RecipientId recipientId) {
        final var serviceId = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId).getServiceId();
        return trustIdentity(serviceId, identityKey -> true, TrustLevel.TRUSTED_UNVERIFIED);
    }

    public String computeSafetyNumber(ServiceId serviceId, IdentityKey theirIdentityKey) {
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(serviceId, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public ScannableFingerprint computeSafetyNumberForScanning(ServiceId serviceId, IdentityKey theirIdentityKey) {
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(serviceId, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getScannableFingerprint();
    }

    private Fingerprint computeSafetyNumberFingerprint(
            final ServiceId serviceId, final IdentityKey theirIdentityKey
    ) {
        final var address = account.getRecipientAddressResolver()
                .resolveRecipientAddress(account.getRecipientResolver().resolveRecipient(serviceId));

        return Utils.computeSafetyNumber(capabilities.isUuid(),
                account.getSelfRecipientAddress(),
                account.getAciIdentityKeyPair().getPublicKey(),
                address.getServiceId().equals(serviceId)
                        ? address
                        : new RecipientAddress(serviceId.uuid(), address.number().orElse(null)),
                theirIdentityKey);
    }

    private boolean trustIdentity(
            ServiceId serviceId, Function<IdentityKey, Boolean> verifier, TrustLevel trustLevel
    ) {
        var identity = account.getIdentityKeyStore().getIdentityInfo(serviceId);
        if (identity == null) {
            return false;
        }

        if (!verifier.apply(identity.getIdentityKey())) {
            return false;
        }

        account.getIdentityKeyStore().setIdentityTrustLevel(serviceId, identity.getIdentityKey(), trustLevel);
        try {
            final var address = account.getRecipientAddressResolver()
                    .resolveRecipientAddress(account.getRecipientResolver().resolveRecipient(serviceId))
                    .toSignalServiceAddress();
            context.getSyncHelper().sendVerifiedMessage(address, identity.getIdentityKey(), trustLevel);
        } catch (IOException e) {
            logger.warn("Failed to send verification sync message: {}", e.getMessage());
        }

        return true;
    }

    public void handleIdentityFailure(
            final RecipientId recipientId,
            final ServiceId serviceId,
            final SendMessageResult.IdentityFailure identityFailure
    ) {
        final var identityKey = identityFailure.getIdentityKey();
        if (identityKey != null) {
            account.getIdentityKeyStore().saveIdentity(serviceId, identityKey);
        } else {
            // Retrieve profile to get the current identity key from the server
            context.getProfileHelper().refreshRecipientProfile(recipientId);
        }
    }
}
