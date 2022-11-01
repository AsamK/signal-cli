package org.asamk.signal.manager.config;

import org.bouncycastle.util.encoders.Hex;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;

import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okhttp3.Dns;
import okhttp3.Interceptor;

class StagingConfig {

    private final static byte[] UNIDENTIFIED_SENDER_TRUST_ROOT = Base64.getDecoder()
            .decode("BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx");
    private final static String CDS_MRENCLAVE = "74778bb0f93ae1f78c26e67152bab0bbeb693cd56d1bb9b4e9244157acc58081";
    private final static String CDSI_MRENCLAVE = "ef4787a56a154ac6d009138cac17155acd23cfe4329281252365dd7c252e7fbf";

    private final static String KEY_BACKUP_ENCLAVE_NAME = "39963b736823d5780be96ab174869a9499d56d66497aa8f9b2244f777ebc366b";
    private final static byte[] KEY_BACKUP_SERVICE_ID = Hex.decode(
            "9dbc6855c198e04f21b5cc35df839fdcd51b53658454dfa3f817afefaffc95ef");
    private final static String KEY_BACKUP_MRENCLAVE = "45627094b2ea4a66f4cf0b182858a8dcf4b8479122c3820fe7fd0551a6d4cf5c";
    private final static String FALLBACK_KEY_BACKUP_ENCLAVE_NAME = "dd6f66d397d9e8cf6ec6db238e59a7be078dd50e9715427b9c89b409ffe53f99";
    private final static byte[] FALLBACK_KEY_BACKUP_SERVICE_ID = Hex.decode(
            "4200003414528c151e2dccafbc87aa6d3d66a5eb8f8c05979a6e97cb33cd493a");
    private final static String FALLBACK_KEY_BACKUP_MRENCLAVE = "ee19f1965b1eefa3dc4204eb70c04f397755f771b8c1909d080c04dad2a6a9ba";

    private final static String URL = "https://chat.staging.signal.org";
    private final static String CDN_URL = "https://cdn-staging.signal.org";
    private final static String CDN2_URL = "https://cdn2-staging.signal.org";
    private final static String SIGNAL_CONTACT_DISCOVERY_URL = "https://api-staging.directory.signal.org";
    private final static String SIGNAL_KEY_BACKUP_URL = "https://api-staging.backup.signal.org";
    private final static String STORAGE_URL = "https://storage-staging.signal.org";
    private final static String SIGNAL_CDSI_URL = "https://cdsi.staging.signal.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();

    private final static Optional<Dns> dns = Optional.empty();
    private final static Optional<SignalProxy> proxy = Optional.empty();

    private final static byte[] zkGroupServerPublicParams = Base64.getDecoder()
            .decode("ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUj");

    static SignalServiceConfiguration createDefaultServiceConfiguration(
            final List<Interceptor> interceptors
    ) {
        return new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(URL, TRUST_STORE)},
                Map.of(0,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN_URL, TRUST_STORE)},
                        2,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN2_URL, TRUST_STORE)}),
                new SignalContactDiscoveryUrl[]{
                        new SignalContactDiscoveryUrl(SIGNAL_CONTACT_DISCOVERY_URL, TRUST_STORE)
                },
                new SignalKeyBackupServiceUrl[]{new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, TRUST_STORE)},
                new SignalStorageUrl[]{new SignalStorageUrl(STORAGE_URL, TRUST_STORE)},
                new SignalCdsiUrl[]{new SignalCdsiUrl(SIGNAL_CDSI_URL, TRUST_STORE)},
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

    static Collection<KeyBackupConfig> createFallbackKeyBackupConfigs() {
        return List.of(new KeyBackupConfig(FALLBACK_KEY_BACKUP_ENCLAVE_NAME,
                FALLBACK_KEY_BACKUP_SERVICE_ID,
                FALLBACK_KEY_BACKUP_MRENCLAVE));
    }

    static String getCdsMrenclave() {
        return CDS_MRENCLAVE;
    }

    static String getCdsiMrenclave() {
        return CDSI_MRENCLAVE;
    }

    private StagingConfig() {
    }
}
