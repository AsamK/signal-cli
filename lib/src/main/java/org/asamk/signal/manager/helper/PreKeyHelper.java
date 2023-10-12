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
import org.whispersystems.signalservice.internal.push.OneTimePreKeyCounts;

import java.io.IOException;
import java.util.List;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_STALE_AGE;
import static org.asamk.signal.manager.config.ServiceConfig.SIGNED_PREKEY_ROTATE_AGE;

public class PreKeyHelper {

    private final static Logger logger = LoggerFactory.getLogger(PreKeyHelper.class);

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

    public void refreshPreKeysIfNecessary(ServiceIdType serviceIdType) throws IOException {
        final var identityKeyPair = account.getIdentityKeyPair(serviceIdType);
        if (identityKeyPair == null) {
            return;
        }
        final var accountId = account.getAccountId(serviceIdType);
        if (accountId == null) {
            return;
        }

        OneTimePreKeyCounts preKeyCounts;
        try {
            preKeyCounts = dependencies.getAccountManager().getPreKeyCounts(serviceIdType);
        } catch (AuthorizationFailedException e) {
            logger.debug("Failed to get pre key count, ignoring: " + e.getClass().getSimpleName());
            preKeyCounts = new OneTimePreKeyCounts(0, 0);
        }

        SignedPreKeyRecord signedPreKeyRecord = null;
        List<PreKeyRecord> preKeyRecords = null;
        KyberPreKeyRecord lastResortKyberPreKeyRecord = null;
        List<KyberPreKeyRecord> kyberPreKeyRecords = null;

        try {
            if (preKeyCounts.getEcCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
                logger.debug("Refreshing {} ec pre keys, because only {} of min {} pre keys remain",
                        serviceIdType,
                        preKeyCounts.getEcCount(),
                        ServiceConfig.PREKEY_MINIMUM_COUNT);
                preKeyRecords = generatePreKeys(serviceIdType);
            }
            if (signedPreKeyNeedsRefresh(serviceIdType)) {
                logger.debug("Refreshing {} signed pre key.", serviceIdType);
                signedPreKeyRecord = generateSignedPreKey(serviceIdType, identityKeyPair);
            }
        } catch (Exception e) {
            logger.warn("Failed to store new pre keys, resetting preKey id offset", e);
            account.resetPreKeyOffsets(serviceIdType);
            preKeyRecords = generatePreKeys(serviceIdType);
            signedPreKeyRecord = generateSignedPreKey(serviceIdType, identityKeyPair);
        }

        try {
            if (preKeyCounts.getKyberCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
                logger.debug("Refreshing {} kyber pre keys, because only {} of min {} pre keys remain",
                        serviceIdType,
                        preKeyCounts.getKyberCount(),
                        ServiceConfig.PREKEY_MINIMUM_COUNT);
                kyberPreKeyRecords = generateKyberPreKeys(serviceIdType, identityKeyPair);
            }
            if (lastResortKyberPreKeyNeedsRefresh(serviceIdType)) {
                logger.debug("Refreshing {} last resort kyber pre key.", serviceIdType);
                lastResortKyberPreKeyRecord = generateLastResortKyberPreKey(serviceIdType, identityKeyPair);
            }
        } catch (Exception e) {
            logger.warn("Failed to store new kyber pre keys, resetting preKey id offset", e);
            account.resetKyberPreKeyOffsets(serviceIdType);
            kyberPreKeyRecords = generateKyberPreKeys(serviceIdType, identityKeyPair);
            lastResortKyberPreKeyRecord = generateLastResortKyberPreKey(serviceIdType, identityKeyPair);
        }

        if (signedPreKeyRecord != null
                || preKeyRecords != null
                || lastResortKyberPreKeyRecord != null
                || kyberPreKeyRecords != null) {
            final var preKeyUpload = new PreKeyUpload(serviceIdType,
                    identityKeyPair.getPublicKey(),
                    signedPreKeyRecord,
                    preKeyRecords,
                    lastResortKyberPreKeyRecord,
                    kyberPreKeyRecords);
            try {
                dependencies.getAccountManager().setPreKeys(preKeyUpload);
            } catch (AuthorizationFailedException e) {
                // This can happen when the primary device has changed phone number
                logger.warn("Failed to updated pre keys: {}", e.getMessage());
            }
        }

        cleanSignedPreKeys((serviceIdType));
        cleanOneTimePreKeys(serviceIdType);
    }

    private List<PreKeyRecord> generatePreKeys(ServiceIdType serviceIdType) {
        final var accountData = account.getAccountData(serviceIdType);
        final var offset = accountData.getPreKeyMetadata().getNextPreKeyId();

        var records = KeyUtils.generatePreKeyRecords(offset);
        account.addPreKeys(serviceIdType, records);

        return records;
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

        var record = KeyUtils.generateSignedPreKeyRecord(signedPreKeyId, identityKeyPair.getPrivateKey());
        account.addSignedPreKey(serviceIdType, record);

        return record;
    }

    private List<KyberPreKeyRecord> generateKyberPreKeys(
            ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) {
        final var accountData = account.getAccountData(serviceIdType);
        final var offset = accountData.getPreKeyMetadata().getNextKyberPreKeyId();

        var records = KeyUtils.generateKyberPreKeyRecords(offset, identityKeyPair.getPrivateKey());
        account.addKyberPreKeys(serviceIdType, records);

        return records;
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
            ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair
    ) {
        final var accountData = account.getAccountData(serviceIdType);
        final var signedPreKeyId = accountData.getPreKeyMetadata().getNextKyberPreKeyId();

        var record = KeyUtils.generateKyberPreKeyRecord(signedPreKeyId, identityKeyPair.getPrivateKey());
        account.addLastResortKyberPreKey(serviceIdType, record);

        return record;
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
