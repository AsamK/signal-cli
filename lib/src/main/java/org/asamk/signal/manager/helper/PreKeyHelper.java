package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        if (dependencies.getAccountManager().getPreKeysCount(serviceIdType) < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            refreshPreKeys(serviceIdType);
        }
    }

    public void refreshPreKeys() throws IOException {
        refreshPreKeys(ServiceIdType.ACI);
        refreshPreKeys(ServiceIdType.PNI);
    }

    public void refreshPreKeys(ServiceIdType serviceIdType) throws IOException {
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

        dependencies.getAccountManager()
                .setPreKeys(serviceIdType, identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private List<PreKeyRecord> generatePreKeys(ServiceIdType serviceIdType) {
        final var offset = account.getPreKeyIdOffset(serviceIdType);

        var records = KeyUtils.generatePreKeyRecords(offset, ServiceConfig.PREKEY_BATCH_SIZE);
        account.addPreKeys(serviceIdType, records);

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(ServiceIdType serviceIdType, IdentityKeyPair identityKeyPair) {
        final var signedPreKeyId = account.getNextSignedPreKeyId(serviceIdType);

        var record = KeyUtils.generateSignedPreKeyRecord(identityKeyPair, signedPreKeyId);
        account.addSignedPreKey(serviceIdType, record);

        return record;
    }
}
