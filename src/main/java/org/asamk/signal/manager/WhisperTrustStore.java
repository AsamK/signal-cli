package org.asamk.signal.manager;

import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

class WhisperTrustStore implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
        return WhisperTrustStore.class.getResourceAsStream("whisper.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
}
