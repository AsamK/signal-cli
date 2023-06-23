package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.io.IOException;
import java.util.List;

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
        final var preKeyCounts = dependencies.getAccountManager().getPreKeyCounts(serviceIdType);
        if (preKeyCounts.getEcCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            logger.debug("Refreshing {} ec pre keys, because only {} of {} pre keys remain",
                    serviceIdType,
                    preKeyCounts.getEcCount(),
                    ServiceConfig.PREKEY_MINIMUM_COUNT);
            refreshPreKeys(serviceIdType);
        }
        if (preKeyCounts.getKyberCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            logger.debug("Refreshing {} kyber pre keys, because only {} of {} pre keys remain",
                    serviceIdType,
                    preKeyCounts.getEcCount(),
                    ServiceConfig.PREKEY_MINIMUM_COUNT);
            refreshKyberPreKeys(serviceIdType);
        }
    }

    private void refreshPreKeys(ServiceIdType serviceIdType) throws IOException {
        final var identityKeyPair = account.getIdentityKeyPair(serviceIdType);
        if (identityKeyPair == null) {
            return;
        }
        final var accountId = account.getAccountId(serviceIdType);
        if (accountId == null) {
            return;
        }
        try {
            refreshPreKeys(serviceIdType, identityKeyPair);
        } catch (Exception e) {
            logger.warn("Failed to store new pre keys, resetting preKey id offset", e);
            account.resetPreKeyOffsets(serviceIdType);
            refreshPreKeys(serviceIdType, identityKeyPair);
        }
    }

    private void refreshPreKeys(
            final ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) throws IOException {
        final var oneTimePreKeys = generatePreKeys(serviceIdType);
        final var signedPreKeyRecord = generateSignedPreKey(serviceIdType, identityKeyPair);

        final var preKeyUpload = new PreKeyUpload(serviceIdType,
                identityKeyPair.getPublicKey(),
                signedPreKeyRecord,
                oneTimePreKeys,
                null,
                null);
        dependencies.getAccountManager().setPreKeys(preKeyUpload);
    }

    private List<PreKeyRecord> generatePreKeys(ServiceIdType serviceIdType) {
        final var offset = account.getPreKeyIdOffset(serviceIdType);

        var records = KeyUtils.generatePreKeyRecords(offset);
        account.addPreKeys(serviceIdType, records);

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair) {
        final var signedPreKeyId = account.getNextSignedPreKeyId(serviceIdType);

        var record = KeyUtils.generateSignedPreKeyRecord(signedPreKeyId, identityKeyPair);
        account.addSignedPreKey(serviceIdType, record);

        return record;
    }

    private void refreshKyberPreKeys(ServiceIdType serviceIdType) throws IOException {
        final var identityKeyPair = account.getIdentityKeyPair(serviceIdType);
        if (identityKeyPair == null) {
            return;
        }
        final var accountId = account.getAccountId(serviceIdType);
        if (accountId == null) {
            return;
        }
        try {
            refreshKyberPreKeys(serviceIdType, identityKeyPair);
        } catch (Exception e) {
            logger.warn("Failed to store new pre keys, resetting preKey id offset", e);
            account.resetKyberPreKeyOffsets(serviceIdType);
            refreshKyberPreKeys(serviceIdType, identityKeyPair);
        }
    }

    private void refreshKyberPreKeys(
            final ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) throws IOException {
        final var oneTimePreKeys = generateKyberPreKeys(serviceIdType, identityKeyPair);
        final var lastResortPreKeyRecord = generateLastResortKyberPreKey(serviceIdType, identityKeyPair);

        final var preKeyUpload = new PreKeyUpload(serviceIdType,
                identityKeyPair.getPublicKey(),
                null,
                null,
                lastResortPreKeyRecord,
                oneTimePreKeys);
        dependencies.getAccountManager().setPreKeys(preKeyUpload);
    }

    private List<KyberPreKeyRecord> generateKyberPreKeys(
            ServiceIdType serviceIdType, final IdentityKeyPair identityKeyPair
    ) {
        final var offset = account.getKyberPreKeyIdOffset(serviceIdType);

        var records = KeyUtils.generateKyberPreKeyRecords(offset, identityKeyPair.getPrivateKey());
        account.addKyberPreKeys(serviceIdType, records);

        return records;
    }

    private KyberPreKeyRecord generateLastResortKyberPreKey(
            ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair
    ) {
        final var signedPreKeyId = account.getKyberPreKeyIdOffset(serviceIdType);

        var record = KeyUtils.generateKyberPreKeyRecord(signedPreKeyId, identityKeyPair.getPrivateKey());
        account.addLastResortKyberPreKey(serviceIdType, record);

        return record;
    }
}
