package org.asamk.signal.manager.config;

import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

class IasTrustStore implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
        return IasTrustStore.class.getResourceAsStream("ias.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
}
