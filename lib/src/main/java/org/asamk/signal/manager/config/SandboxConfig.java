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

class SandboxConfig {

    private final static byte[] UNIDENTIFIED_SENDER_TRUST_ROOT = Base64.getDecoder()
            .decode("BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx");
    private final static String CDS_MRENCLAVE = "c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15";

    private final static String KEY_BACKUP_ENCLAVE_NAME = "823a3b2c037ff0cbe305cc48928cfcc97c9ed4a8ca6d49af6f7d6981fb60a4e9";
    private final static byte[] KEY_BACKUP_SERVICE_ID = Hex.decode(
            "51a56084c0b21c6b8f62b1bc792ec9bedac4c7c3964bb08ddcab868158c09982");
    private final static String KEY_BACKUP_MRENCLAVE = "a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87";

    private final static String URL = "https://textsecure-service-staging.whispersystems.org";
    private final static String CDN_URL = "https://cdn-staging.signal.org";
    private final static String CDN2_URL = "https://cdn2-staging.signal.org";
    private final static String SIGNAL_CONTACT_DISCOVERY_URL = "https://api-staging.directory.signal.org";
    private final static String SIGNAL_KEY_BACKUP_URL = "https://api-staging.backup.signal.org";
    private final static String STORAGE_URL = "https://storage-staging.signal.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    private final static Optional<Dns> dns = Optional.absent();
    private final static Optional<SignalProxy> proxy = Optional.absent();

    private final static byte[] zkGroupServerPublicParams = Base64.getDecoder()
            .decode("ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdls=");

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

    private SandboxConfig() {
    }
}
