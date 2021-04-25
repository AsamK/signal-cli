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
        final var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withParallelism(1)
                .withIterations(32)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(16 * 1024)
                .withSalt(hashSession.hashSalt())
                .build();

        final var generator = new Argon2BytesGenerator();
        generator.init(params);

        return PinHasher.hashPin(PinHasher.normalize(pin), password -> {
            var output = new byte[64];
            generator.generateBytes(password, output);
            return output;
        });
    }
}
