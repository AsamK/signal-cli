package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

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
        if (dependencies.getAccountManager().getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
            refreshPreKeys();
        }
    }

    public void refreshPreKeys() throws IOException {
        var oneTimePreKeys = generatePreKeys();
        final var identityKeyPair = account.getIdentityKeyPair();
        var signedPreKeyRecord = generateSignedPreKey(identityKeyPair);

        dependencies.getAccountManager().setPreKeys(identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private List<PreKeyRecord> generatePreKeys() {
        final var offset = account.getPreKeyIdOffset();

        var records = KeyUtils.generatePreKeyRecords(offset, ServiceConfig.PREKEY_BATCH_SIZE);
        account.addPreKeys(records);

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) {
        final var signedPreKeyId = account.getNextSignedPreKeyId();

        var record = KeyUtils.generateSignedPreKeyRecord(identityKeyPair, signedPreKeyId);
        account.addSignedPreKey(record);

        return record;
    }
}
