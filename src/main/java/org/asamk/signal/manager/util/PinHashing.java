package org.asamk.signal.manager.util;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.internal.registrationpin.PinHasher;

public final class PinHashing {

    private PinHashing() {
    }

    public static HashedPin hashPin(String pin, KeyBackupService.HashSession hashSession) {
        final Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withParallelism(1)
                .withIterations(32)
                .withVersion(13)
                .withMemoryAsKB(16 * 1024)
                .withSalt(hashSession.hashSalt())
                .build();

        final Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        return PinHasher.hashPin(PinHasher.normalize(pin), password -> {
            byte[] output = new byte[64];
            generator.generateBytes(password, output);
            return output;
        });
    }
}
