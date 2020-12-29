package org.asamk.signal.manager;

import org.signal.zkgroup.ServerPublicParams;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.Interceptor;

public class ServiceConfig {

    final static String UNIDENTIFIED_SENDER_TRUST_ROOT = "BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF";
    final static int PREKEY_MINIMUM_COUNT = 20;
    final static int PREKEY_BATCH_SIZE = 100;
    final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
    final static int MAX_ENVELOPE_SIZE = 0;
    final static long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = 10 * 1024 * 1024;

    final static String CDS_MRENCLAVE = "c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15";

    private final static String URL = "https://textsecure-service.whispersystems.org";
    private final static String CDN_URL = "https://cdn.signal.org";
    private final static String CDN2_URL = "https://cdn2.signal.org";
    private final static String SIGNAL_CONTACT_DISCOVERY_URL = "https://api.directory.signal.org";
    private final static String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    private final static String STORAGE_URL = "https://storage.signal.org";
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();
    private final static TrustStore IAS_TRUST_STORE = new IasTrustStore();

    private final static Optional<Dns> dns = Optional.absent();

    private final static String zkGroupServerPublicParamsHex = "AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X0=";
    private final static byte[] zkGroupServerPublicParams;

    static final AccountAttributes.Capabilities capabilities;

    static {
        try {
            zkGroupServerPublicParams = Base64.decode(zkGroupServerPublicParamsHex);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        boolean zkGroupAvailable;
        try {
            new ServerPublicParams(zkGroupServerPublicParams);
            zkGroupAvailable = true;
        } catch (Throwable ignored) {
            zkGroupAvailable = false;
        }
        capabilities = new AccountAttributes.Capabilities(false, zkGroupAvailable, false, zkGroupAvailable);
    }

    public static SignalServiceConfiguration createDefaultServiceConfiguration(String userAgent) {
        final Interceptor userAgentInterceptor = chain -> chain.proceed(chain.request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build());

        final List<Interceptor> interceptors = Collections.singletonList(userAgentInterceptor);

        return new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(URL, TRUST_STORE)},
                makeSignalCdnUrlMapFor(new SignalCdnUrl[]{new SignalCdnUrl(CDN_URL, TRUST_STORE)},
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN2_URL, TRUST_STORE)}),
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl(SIGNAL_CONTACT_DISCOVERY_URL,
                        TRUST_STORE)},
                new SignalKeyBackupServiceUrl[]{new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, TRUST_STORE)},
                new SignalStorageUrl[]{new SignalStorageUrl(STORAGE_URL, TRUST_STORE)},
                interceptors,
                dns,
                zkGroupServerPublicParams);
    }

    public static AccountAttributes.Capabilities getCapabilities() {
        return capabilities;
    }

    static KeyStore getIasKeyStore() {
        try {
            TrustStore contactTrustStore = IAS_TRUST_STORE;

            KeyStore keyStore = KeyStore.getInstance("BKS");
            keyStore.load(contactTrustStore.getKeyStoreInputStream(),
                    contactTrustStore.getKeyStorePassword().toCharArray());

            return keyStore;
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static ECPublicKey getUnidentifiedSenderTrustRoot() {
        try {
            return Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<Integer, SignalCdnUrl[]> makeSignalCdnUrlMapFor(
            SignalCdnUrl[] cdn0Urls, SignalCdnUrl[] cdn2Urls
    ) {
        return Map.of(0, cdn0Urls, 2, cdn2Urls);
    }

    private ServiceConfig() {
    }
}
