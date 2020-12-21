package org.asamk.signal.util;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

public class SecurityProvider extends Provider {

    private static final String PROVIDER_NAME = "SSP";

    private static final String info = "Security Provider v1.0";

    public SecurityProvider() {
        super(PROVIDER_NAME, "1.0", info);
        put("SecureRandom.DEFAULT", DefaultRandom.class.getName());

        // Workaround for BKS truststore
        put("KeyStore.BKS", org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi.Std.class.getCanonicalName());
        put("KeyStore.BKS-V1",
                org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi.Version1.class.getCanonicalName());
        put("KeyStore.BouncyCastle",
                org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi.BouncyCastleStore.class.getCanonicalName());
        put("KeyFactory.X.509", org.bouncycastle.jcajce.provider.asymmetric.x509.KeyFactory.class.getCanonicalName());
        put("CertificateFactory.X.509",
                org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory.class.getCanonicalName());
    }

    public static class DefaultRandom extends SecureRandomSpi {

        private static final SecureRandom random = RandomUtils.getSecureRandom();

        public DefaultRandom() {
        }

        protected void engineSetSeed(byte[] bytes) {
            random.setSeed(bytes);
        }

        protected void engineNextBytes(byte[] bytes) {
            random.nextBytes(bytes);
        }

        protected byte[] engineGenerateSeed(int numBytes) {
            return random.generateSeed(numBytes);
        }
    }
}
