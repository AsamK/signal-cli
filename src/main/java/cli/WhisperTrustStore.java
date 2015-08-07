package cli;

import org.whispersystems.textsecure.api.push.TrustStore;

import java.io.InputStream;

class WhisperTrustStore implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
        return cli.WhisperTrustStore.class.getResourceAsStream("whisper.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
}
