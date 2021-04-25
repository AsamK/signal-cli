package org.asamk.signal.manager.config;

import org.bouncycastle.util.encoders.Hex;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.Interceptor;

class LiveConfig {

    private final static byte[] UNIDENTIFIED_SENDER_TRUST_ROOT = Base64.getDecoder()
            .decode("BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF");
    private final static String CDS_MRENCLAVE = "c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15";

    private final static String KEY_BACKUP_ENCLAVE_NAME = "fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe";
    private final static byte[] KEY_BACKUP_SERVICE_ID = Hex.decode(
            "fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe");
    private final static String KEY_BACKUP_MRENCLAVE = "a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87";

    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static String CDN_URL = "https://cdn.signal.org";
    private final static String CDN2_URL = "https://cdn2.signal.org";
    private final static String SIGNAL_CONTACT_DISCOVERY_URL = "https://api.directory.signal.org";
    private final static String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    private final static String STORAGE_URL = "https://storage.signal.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    private final static Optional<Dns> dns = Optional.absent();
    private final static Optional<SignalProxy> proxy = Optional.absent();

    private final static byte[] zkGroupServerPublicParams = Base64.getDecoder()
            .decode("AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X0=");

    static SignalServiceConfiguration createDefaultServiceConfiguration(
            final List<Interceptor> interceptors
    ) {
        return new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(URL, TRUST_STORE)},
                Map.of(0,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN_URL, TRUST_STORE)},
                        2,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN2_URL, TRUST_STORE)}),
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl(SIGNAL_CONTACT_DISCOVERY_URL,
                        TRUST_STORE)},
                new SignalKeyBackupServiceUrl[]{new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, TRUST_STORE)},
                new SignalStorageUrl[]{new SignalStorageUrl(STORAGE_URL, TRUST_STORE)},
                interceptors,
                dns,
                proxy,
                zkGroupServerPublicParams);
    }

    static ECPublicKey getUnidentifiedSenderTrustRoot() {
        try {
            return Curve.decodePoint(UNIDENTIFIED_SENDER_TRUST_ROOT, 0);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    static KeyBackupConfig createKeyBackupConfig() {
        return new KeyBackupConfig(KEY_BACKUP_ENCLAVE_NAME, KEY_BACKUP_SERVICE_ID, KEY_BACKUP_MRENCLAVE);
    }

    static String getCdsMrenclave() {
        return CDS_MRENCLAVE;
    }

    private LiveConfig() {
    }
}
