package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.push.OneTimePreKeyCounts;

import java.io.IOException;
import java.util.List;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_STALE_AGE;
import static org.asamk.signal.manager.config.ServiceConfig.SIGNED_PREKEY_ROTATE_AGE;

public class PreKeyHelper {

    private static final Logger logger = LoggerFactory.getLogger(PreKeyHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;

    public PreKeyHelper(
            final SignalAccount account, final SignalDependencies dependencies
    ) {
        this.account = account;
        this.dependencies = dependencies;
    }

    public void refreshPreKeysIfNecessary() throws IOException {
        refreshPreKeysIfNecessary(ServiceIdType.ACI);
        refreshPreKeysIfNecessary(ServiceIdType.PNI);
    }

    public void forceRefreshPreKeys() throws IOException {
        forceRefreshPreKeys(ServiceIdType.ACI);
        forceRefreshPreKeys(ServiceIdType.PNI);
    }

    public void refreshPreKeysIfNecessary(ServiceIdType serviceIdType) throws IOException {
        final var identityKeyPair = account.getIdentityKeyPair(serviceIdType);
        if (identityKeyPair == null) {
            return;
        }
        final var accountId = account.getAccountId(serviceIdType);
        if (accountId == null) {
            return;
        }

        if (refreshPreKeysIfNecessary(serviceIdType, identityKeyPair)) {
            refreshPreKeysIfNecessary(serviceIdType, identityKeyPair);
        }
    }

    public void forceRefreshPreKeys(ServiceIdType serviceIdType) throws IOException {
        final var identityKeyPair = account.getIdentityKeyPair(serviceIdType);
        if (identityKeyPair == null) {
            return;
        }
        final var accountId = account.getAccountId(serviceIdType);
        if (accountId == null) {
            return;
        }

        final var counts = new OneTimePreKeyCounts(0, 0);
        if (refreshPreKeysIfNecessary(serviceIdType, identityKeyPair, counts, true)) {
            refreshPreKeysIfNecessary(serviceIdType, identityKeyPair, counts, true);
        }
    }

    private boolean refreshPreKeysIfNecessary(
            final ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) throws IOException {
        OneTimePreKeyCounts preKeyCounts;
        try {
            preKeyCounts = dependencies.getAccountManager().getPreKeyCounts(serviceIdType);
        } catch (AuthorizationFailedException e) {
            logger.debug("Failed to get pre key count, ignoring: " + e.getClass().getSimpleName());
            preKeyCounts = new OneTimePreKeyCounts(0, 0);
        }

        return refreshPreKeysIfNecessary(serviceIdType, identityKeyPair, preKeyCounts, false);
    }

    private boolean refreshPreKeysIfNecessary(
            final ServiceIdType serviceIdType,
            final IdentityKeyPair identityKeyPair,
            final OneTimePreKeyCounts preKeyCounts,
            final boolean force
    ) throws IOException {
        List<PreKeyRecord> preKeyRecords = null;
        if (force || preKeyCounts.getEcCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            logger.debug("Refreshing {} ec pre keys, because only {} of min {} pre keys remain",
                    serviceIdType,
                    preKeyCounts.getEcCount(),
                    ServiceConfig.PREKEY_MINIMUM_COUNT);
            preKeyRecords = generatePreKeys(serviceIdType);
        }

        SignedPreKeyRecord signedPreKeyRecord = null;
        if (force || signedPreKeyNeedsRefresh(serviceIdType)) {
            logger.debug("Refreshing {} signed pre key.", serviceIdType);
            signedPreKeyRecord = generateSignedPreKey(serviceIdType, identityKeyPair);
        }

        List<KyberPreKeyRecord> kyberPreKeyRecords = null;
        if (force || preKeyCounts.getKyberCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            logger.debug("Refreshing {} kyber pre keys, because only {} of min {} pre keys remain",
                    serviceIdType,
                    preKeyCounts.getKyberCount(),
                    ServiceConfig.PREKEY_MINIMUM_COUNT);
            kyberPreKeyRecords = generateKyberPreKeys(serviceIdType, identityKeyPair);
        }

        KyberPreKeyRecord lastResortKyberPreKeyRecord = null;
        if (force || lastResortKyberPreKeyNeedsRefresh(serviceIdType)) {
            logger.debug("Refreshing {} last resort kyber pre key.", serviceIdType);
            lastResortKyberPreKeyRecord = generateLastResortKyberPreKey(serviceIdType,
                    identityKeyPair,
                    kyberPreKeyRecords == null ? 0 : kyberPreKeyRecords.size());
        }

        if (signedPreKeyRecord == null
                && preKeyRecords == null
                && lastResortKyberPreKeyRecord == null
                && kyberPreKeyRecords == null) {
            return false;
        }

        final var preKeyUpload = new PreKeyUpload(serviceIdType,
                signedPreKeyRecord,
                preKeyRecords,
                lastResortKyberPreKeyRecord,
                kyberPreKeyRecords);
        var needsReset = false;
        try {
            dependencies.getAccountManager().setPreKeys(preKeyUpload);
            try {
                if (preKeyRecords != null) {
                    account.addPreKeys(serviceIdType, preKeyRecords);
                }
                if (signedPreKeyRecord != null) {
                    account.addSignedPreKey(serviceIdType, signedPreKeyRecord);
                }
            } catch (Exception e) {
                logger.warn("Failed to store new pre keys, resetting preKey id offset", e);
                account.resetPreKeyOffsets(serviceIdType);
                needsReset = true;
            }
            try {
                if (kyberPreKeyRecords != null) {
                    account.addKyberPreKeys(serviceIdType, kyberPreKeyRecords);
                }
                if (lastResortKyberPreKeyRecord != null) {
                    account.addLastResortKyberPreKey(serviceIdType, lastResortKyberPreKeyRecord);
                }
            } catch (Exception e) {
                logger.warn("Failed to store new kyber pre keys, resetting preKey id offset", e);
                account.resetKyberPreKeyOffsets(serviceIdType);
                needsReset = true;
            }
        } catch (AuthorizationFailedException e) {
            // This can happen when the primary device has changed phone number
            logger.warn("Failed to updated pre keys: {}", e.getMessage());
        } catch (NonSuccessfulResponseCodeException e) {
            if (serviceIdType != ServiceIdType.PNI || e.getCode() != 422) {
                throw e;
            }
            logger.warn("Failed to set PNI pre keys, ignoring for now. Account needs to be reregistered to fix this.");
        }
        return needsReset;
    }

    public void cleanOldPreKeys() {
        cleanOldPreKeys(ServiceIdType.ACI);
        cleanOldPreKeys(ServiceIdType.PNI);
    }

    private void cleanOldPreKeys(final ServiceIdType serviceIdType) {
        cleanSignedPreKeys(serviceIdType);
        cleanOneTimePreKeys(serviceIdType);
    }

    private List<PreKeyRecord> generatePreKeys(ServiceIdType serviceIdType) {
        final var accountData = account.getAccountData(serviceIdType);
        final var offset = accountData.getPreKeyMetadata().getNextPreKeyId();

        return KeyUtils.generatePreKeyRecords(offset);
    }

    private boolean signedPreKeyNeedsRefresh(ServiceIdType serviceIdType) {
        final var accountData = account.getAccountData(serviceIdType);

        final var activeSignedPreKeyId = accountData.getPreKeyMetadata().getActiveSignedPreKeyId();
        if (activeSignedPreKeyId == -1) {
            return true;
        }
        try {
            final var signedPreKeyRecord = accountData.getSignedPreKeyStore().loadSignedPreKey(activeSignedPreKeyId);
            return signedPreKeyRecord.getTimestamp() < System.currentTimeMillis() - SIGNED_PREKEY_ROTATE_AGE;
        } catch (InvalidKeyIdException e) {
            return true;
        }
    }

    private SignedPreKeyRecord generateSignedPreKey(ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair) {
        final var accountData = account.getAccountData(serviceIdType);
        final var signedPreKeyId = accountData.getPreKeyMetadata().getNextSignedPreKeyId();

        return KeyUtils.generateSignedPreKeyRecord(signedPreKeyId, identityKeyPair.getPrivateKey());
    }

    private List<KyberPreKeyRecord> generateKyberPreKeys(
            ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) {
        final var accountData = account.getAccountData(serviceIdType);
        final var offset = accountData.getPreKeyMetadata().getNextKyberPreKeyId();

        return KeyUtils.generateKyberPreKeyRecords(offset, identityKeyPair.getPrivateKey());
    }

    private boolean lastResortKyberPreKeyNeedsRefresh(ServiceIdType serviceIdType) {
        final var accountData = account.getAccountData(serviceIdType);

        final var activeLastResortKyberPreKeyId = accountData.getPreKeyMetadata().getActiveLastResortKyberPreKeyId();
        if (activeLastResortKyberPreKeyId == -1) {
            return true;
        }
        try {
            final var kyberPreKeyRecord = accountData.getKyberPreKeyStore()
                    .loadKyberPreKey(activeLastResortKyberPreKeyId);
            return kyberPreKeyRecord.getTimestamp() < System.currentTimeMillis() - SIGNED_PREKEY_ROTATE_AGE;
        } catch (InvalidKeyIdException e) {
            return true;
        }
    }

    private KyberPreKeyRecord generateLastResortKyberPreKey(
            ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair, final int offset
    ) {
        final var accountData = account.getAccountData(serviceIdType);
        final var signedPreKeyId = accountData.getPreKeyMetadata().getNextKyberPreKeyId() + offset;

        return KeyUtils.generateKyberPreKeyRecord(signedPreKeyId, identityKeyPair.getPrivateKey());
    }

    private void cleanSignedPreKeys(ServiceIdType serviceIdType) {
        final var accountData = account.getAccountData(serviceIdType);

        final var activeSignedPreKeyId = accountData.getPreKeyMetadata().getActiveSignedPreKeyId();
        accountData.getSignedPreKeyStore().removeOldSignedPreKeys(activeSignedPreKeyId);

        final var activeLastResortKyberPreKeyId = accountData.getPreKeyMetadata().getActiveLastResortKyberPreKeyId();
        accountData.getKyberPreKeyStore().removeOldLastResortKyberPreKeys(activeLastResortKyberPreKeyId);
    }

    private void cleanOneTimePreKeys(ServiceIdType serviceIdType) {
        long threshold = System.currentTimeMillis() - PREKEY_STALE_AGE;
        int minCount = 200;

        final var accountData = account.getAccountData(serviceIdType);
        accountData.getPreKeyStore().deleteAllStaleOneTimeEcPreKeys(threshold, minCount);
        accountData.getKyberPreKeyStore().deleteAllStaleOneTimeKyberPreKeys(threshold, minCount);
    }
}
