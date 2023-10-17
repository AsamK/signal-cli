package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.TrustLevel;
import org.asamk.signal.manager.storage.SignalAccount;
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

import java.util.Arrays;
import java.util.function.BiFunction;

public class IdentityHelper {

    private final static Logger logger = LoggerFactory.getLogger(IdentityHelper.class);

    private final SignalAccount account;
    private final Context context;

    public IdentityHelper(final Context context) {
        this.account = context.getAccount();
        this.context = context;
    }

    public boolean trustIdentityVerified(RecipientId recipientId, byte[] fingerprint) {
        return trustIdentity(recipientId,
                (serviceId, identityKey) -> Arrays.equals(identityKey.serialize(), fingerprint),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, String safetyNumber) {
        return trustIdentity(recipientId,
                (serviceId, identityKey) -> safetyNumber.equals(computeSafetyNumber(serviceId, identityKey)),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, byte[] safetyNumber) {
        return trustIdentity(recipientId, (serviceId, identityKey) -> {
            final var fingerprint = computeSafetyNumberForScanning(serviceId, identityKey);
            try {
                return fingerprint != null && fingerprint.compareTo(safetyNumber);
            } catch (FingerprintVersionMismatchException | FingerprintParsingException e) {
                return false;
            }
        }, TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityAllKeys(RecipientId recipientId) {
        return trustIdentity(recipientId, (serviceId, identityKey) -> true, TrustLevel.TRUSTED_UNVERIFIED);
    }

    public String computeSafetyNumber(ServiceId serviceId, IdentityKey theirIdentityKey) {
        final var fingerprint = computeSafetyNumberFingerprint(serviceId, theirIdentityKey, false);
        return fingerprint == null ? null : fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public ScannableFingerprint computeSafetyNumberForScanning(ServiceId serviceId, IdentityKey theirIdentityKey) {
        var fingerprint = computeSafetyNumberFingerprint(serviceId, theirIdentityKey, false);
        if (fingerprint == null) {
            fingerprint = computeSafetyNumberFingerprint(serviceId, theirIdentityKey, true);
        }
        return fingerprint == null ? null : fingerprint.getScannableFingerprint();
    }

    private Fingerprint computeSafetyNumberFingerprint(
            final ServiceId serviceId, final IdentityKey theirIdentityKey, boolean useServiceId
    ) {
        final var recipientId = account.getRecipientResolver().resolveRecipient(serviceId);
        final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);

        if (useServiceId) {
            if (serviceId.isUnknown()) {
                return null;
            }
            return Utils.computeSafetyNumberForUuid(account.getAci(),
                    account.getAciIdentityKeyPair().getPublicKey(),
                    serviceId,
                    theirIdentityKey);
        }
        if (address.number().isEmpty()) {
            return null;
        }
        return Utils.computeSafetyNumberForNumber(account.getNumber(),
                account.getAciIdentityKeyPair().getPublicKey(),
                address.number().get(),
                theirIdentityKey);
    }

    private boolean trustIdentity(
            RecipientId recipientId, BiFunction<ServiceId, IdentityKey, Boolean> verifier, TrustLevel trustLevel
    ) {
        final var address = account.getRecipientAddressResolver().resolveRecipientAddress(recipientId);
        final var serviceId = address.serviceId().orElse(null);
        if (serviceId == null) {
            return false;
        }
        var identity = account.getIdentityKeyStore().getIdentityInfo(serviceId);
        if (identity == null) {
            return false;
        }

        if (!verifier.apply(serviceId, identity.getIdentityKey())) {
            return false;
        }

        account.getIdentityKeyStore().setIdentityTrustLevel(serviceId, identity.getIdentityKey(), trustLevel);
        context.getSyncHelper()
                .sendVerifiedMessage(address.toSignalServiceAddress(), identity.getIdentityKey(), trustLevel);

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
