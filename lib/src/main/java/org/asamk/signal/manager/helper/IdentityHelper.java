package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.libsignal.fingerprint.ScannableFingerprint;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Function;

import static org.asamk.signal.manager.config.ServiceConfig.capabilities;

public class IdentityHelper {

    private final static Logger logger = LoggerFactory.getLogger(IdentityHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final SignalServiceAddressResolver addressResolver;
    private final SyncHelper syncHelper;
    private final ProfileHelper profileHelper;

    public IdentityHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final SignalServiceAddressResolver addressResolver,
            final SyncHelper syncHelper,
            final ProfileHelper profileHelper
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.addressResolver = addressResolver;
        this.syncHelper = syncHelper;
        this.profileHelper = profileHelper;
    }

    public boolean trustIdentityVerified(RecipientId recipientId, byte[] fingerprint) {
        return trustIdentity(recipientId,
                identityKey -> Arrays.equals(identityKey.serialize(), fingerprint),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, String safetyNumber) {
        return trustIdentity(recipientId,
                identityKey -> safetyNumber.equals(computeSafetyNumber(recipientId, identityKey)),
                TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityVerifiedSafetyNumber(RecipientId recipientId, byte[] safetyNumber) {
        return trustIdentity(recipientId, identityKey -> {
            final var fingerprint = computeSafetyNumberForScanning(recipientId, identityKey);
            try {
                return fingerprint != null && fingerprint.compareTo(safetyNumber);
            } catch (FingerprintVersionMismatchException | FingerprintParsingException e) {
                return false;
            }
        }, TrustLevel.TRUSTED_VERIFIED);
    }

    public boolean trustIdentityAllKeys(RecipientId recipientId) {
        return trustIdentity(recipientId, identityKey -> true, TrustLevel.TRUSTED_UNVERIFIED);
    }

    public String computeSafetyNumber(RecipientId recipientId, IdentityKey theirIdentityKey) {
        var address = addressResolver.resolveSignalServiceAddress(recipientId);
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(address, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public ScannableFingerprint computeSafetyNumberForScanning(RecipientId recipientId, IdentityKey theirIdentityKey) {
        var address = addressResolver.resolveSignalServiceAddress(recipientId);
        final Fingerprint fingerprint = computeSafetyNumberFingerprint(address, theirIdentityKey);
        return fingerprint == null ? null : fingerprint.getScannableFingerprint();
    }

    private Fingerprint computeSafetyNumberFingerprint(
            final SignalServiceAddress theirAddress, final IdentityKey theirIdentityKey
    ) {
        return Utils.computeSafetyNumber(capabilities.isUuid(),
                account.getSelfAddress(),
                account.getIdentityKeyPair().getPublicKey(),
                theirAddress,
                theirIdentityKey);
    }

    private boolean trustIdentity(
            RecipientId recipientId, Function<IdentityKey, Boolean> verifier, TrustLevel trustLevel
    ) {
        var identity = account.getIdentityKeyStore().getIdentity(recipientId);
        if (identity == null) {
            return false;
        }

        if (!verifier.apply(identity.getIdentityKey())) {
            return false;
        }

        account.getIdentityKeyStore().setIdentityTrustLevel(recipientId, identity.getIdentityKey(), trustLevel);
        try {
            var address = addressResolver.resolveSignalServiceAddress(recipientId);
            syncHelper.sendVerifiedMessage(address, identity.getIdentityKey(), trustLevel);
        } catch (IOException e) {
            logger.warn("Failed to send verification sync message: {}", e.getMessage());
        }

        return true;
    }

    public void handleIdentityFailure(
            final RecipientId recipientId, final SendMessageResult.IdentityFailure identityFailure
    ) {
        final var identityKey = identityFailure.getIdentityKey();
        if (identityKey != null) {
            final var newIdentity = account.getIdentityKeyStore().saveIdentity(recipientId, identityKey, new Date());
            if (newIdentity) {
                account.getSessionStore().archiveSessions(recipientId);
            }
        } else {
            // Retrieve profile to get the current identity key from the server
            profileHelper.refreshRecipientProfile(recipientId);
        }
    }
}
