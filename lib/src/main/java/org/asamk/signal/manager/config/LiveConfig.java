package org.asamk.signal.manager.config;

import org.signal.libsignal.net.Network.Environment;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.network.config.HttpProxy;
import org.signal.network.config.SignalCdnUrl;
import org.signal.network.config.SignalCdsiUrl;
import org.signal.network.config.SignalProxy;
import org.signal.network.config.SignalServiceConfiguration;
import org.signal.network.config.SignalServiceUrl;
import org.signal.network.config.SignalStorageUrl;
import org.signal.network.config.SignalSvr2Url;
import org.signal.network.config.TrustStore;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okhttp3.Dns;
import okhttp3.Interceptor;

import static org.asamk.signal.manager.api.ServiceEnvironment.LIVE;

class LiveConfig {

    private static final byte[] UNIDENTIFIED_SENDER_TRUST_ROOT = Base64.getDecoder()
            .decode("BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF");
    private static final byte[] UNIDENTIFIED_SENDER_TRUST_ROOT2 = Base64.getDecoder()
            .decode("BUkY0I+9+oPgDCn4+Ac6Iu813yvqkDr/ga8DzLxFxuk6");
    private static final String CDSI_MRENCLAVE = "0f6fd79cdfdaa5b2e6337f534d3baf999318b0c462a7ac1f41297a3e4b424a57";
    private static final String SVR2_MRENCLAVE = "fdbbacdc0c043d0d53fe1440f62728de0386f45ab0a275bd8f99e03a02af355e";
    private static final String SVR2_MRENCLAVE_LEGACY = "ced8217b26228e4b210c985786999d095c4958a94faf37b14acaf25c4cbb02a4";

    private static final String URL = "https://chat.signal.org";
    private static final String CDN_URL = "https://cdn.signal.org";
    private static final String CDN2_URL = "https://cdn2.signal.org";
    private static final String CDN3_URL = "https://cdn3.signal.org";
    private static final String STORAGE_URL = "https://storage.signal.org";
    private static final String SIGNAL_CDSI_URL = "https://cdsi.signal.org";
    private static final String SIGNAL_SVR2_URL = "https://svr2.signal.org";
    private static final TrustStore TRUST_STORE = new WhisperTrustStore();

    private static final Optional<Dns> dns = Optional.empty();
    private static final Optional<SignalProxy> proxy = Optional.empty();
    private static final Optional<HttpProxy> systemProxy = Optional.empty();

    private static final byte[] zkGroupServerPublicParams = Base64.getDecoder()
            .decode("AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXTLfN0/vLt98KDPnxwAQL9j5V1jGOY8jQl6MLxEs56cwXN0dqCnImzVH3TZT1cJ8SW1BRX6qIVxEzjsSGx3yxF3suAilPMqGRp4ffyopjMD1JXiKR2RwLKzizUe5e8XyGOy9fplzhw3jVzTRyUZTRSZKkMLWcQ/gv0E4aONNqs4P+NameAZYOD12qRkxosQQP5uux6B2nRyZ7sAV54DgFyLiRcq1FvwKw2EPQdk4HDoePrO/RNUbyNddnM/mMgj4FW65xCoT1LmjrIjsv/Ggdlx46ueczhMgtBunx1/w8k8V+l8LVZ8gAT6wkU5J+DPQalQguMg12Jzug3q4TbdHiGCmD9EunCwOmsLuLJkz6EcSYXtrlDEnAM+hicw7iergYLLlMXpfTdGxJCWJmP4zqUFeTTmsmhsjGBt7NiEB/9pFFEB3pSbf4iiUukw63Eo8Aqnf4iwob6X1QviCWuc8t0LUlT9vALgh/f2DPVOOmR0RW6bgRvc7DSF20V/omg+YBw==");
    private static final byte[] genericServerPublicParams = Base64.getDecoder()
            .decode("AeCO67P9mIv1yUHkdeZ9JF789GDbox61GvTqq3S4kYc1ADUWxWHQygU390tv1oRWt9WjkdZlU7mKkifF59ftjE+2ZlMmxns6I+ySiLpR8FEmfu+TGpVp3zYTjNV93obJJTyBCCsSHVETCyQRbKdCyb5TMa6LGrvcZaX0Q/VAavhuNA/m4kSiRMgSnYrUjGhVekdDnF+7xioo4wvFnxjIDh7uJQrYOWD6MloNGX7St5gbysTuQQ7i/HI38b9V8x8mKazuDSXxB//BKGZx/XHkK8cHX+QK1MPxYUVM1/CBI5oW");

    private static final byte[] backupServerPublicParams = Base64.getDecoder()
            .decode("AZwNSU55fsFCbgaxGRD11wO1juAs8Yr5GF8FPlGzzvdJJIKH5/4CC7ZJSOe3yL2vturVaRU2Cx0n751Vt8wkj1Y4pyiScu0/S10n647ipo+iq97JZQv+UOlwH8ThyNlGT5DfxXCwTqivxHuXvZpuezPgHk5Gxl5aC6xuNxOnwmFlmu4CeSgdhW8+Pp0vAJOQ1MsU2D0+/kzI+tU94nB3tybY/Ao1AcGW2q41uKQbnOJUWwmQaFT6s+xTISgzsg7CPox6oORGX8rnyk/9lic3DbGsUHctIVpMAl/ogJBb4aYC");

    private static final Environment LIBSIGNAL_NET_ENV = Environment.PRODUCTION;

    static SignalServiceConfiguration createDefaultServiceConfiguration(
            final List<Interceptor> interceptors
    ) {
        return new SignalServiceConfiguration(new SignalServiceUrl[]{new SignalServiceUrl(URL, TRUST_STORE)},
                Map.of(0,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN_URL, TRUST_STORE)},
                        2,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN2_URL, TRUST_STORE)},
                        3,
                        new SignalCdnUrl[]{new SignalCdnUrl(CDN3_URL, TRUST_STORE)}),
                new SignalStorageUrl[]{new SignalStorageUrl(STORAGE_URL, TRUST_STORE)},
                new SignalCdsiUrl[]{new SignalCdsiUrl(SIGNAL_CDSI_URL, TRUST_STORE)},
                new SignalSvr2Url[]{new SignalSvr2Url(SIGNAL_SVR2_URL, TRUST_STORE, null, null)},
                interceptors,
                dns,
                proxy,
                systemProxy,
                zkGroupServerPublicParams,
                genericServerPublicParams,
                backupServerPublicParams,
                false);
    }

    static List<ECPublicKey> getUnidentifiedSenderTrustRoots() {
        try {
            return List.of(new ECPublicKey(UNIDENTIFIED_SENDER_TRUST_ROOT),
                    new ECPublicKey(UNIDENTIFIED_SENDER_TRUST_ROOT2));
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    static ServiceEnvironmentConfig getServiceEnvironmentConfig(List<Interceptor> interceptors) {
        return new ServiceEnvironmentConfig(LIVE,
                LIBSIGNAL_NET_ENV,
                createDefaultServiceConfiguration(interceptors),
                getUnidentifiedSenderTrustRoots(),
                CDSI_MRENCLAVE,
                List.of(SVR2_MRENCLAVE, SVR2_MRENCLAVE_LEGACY));
    }

    private LiveConfig() {
    }
}
