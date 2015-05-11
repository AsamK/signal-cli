package cli;

import org.whispersystems.textsecure.api.push.TrustStore;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class WhisperTrustStore implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
        return cli.WhisperTrustStore.class.getResourceAsStream("whisper.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
}
