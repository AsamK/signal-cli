package org.asamk.signal.manager.util;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;

public class Utils {

    public static String getFileMimeType(File file, String defaultMimeType) throws IOException {
        var mime = Files.probeContentType(file.toPath());
        if (mime == null) {
            try (InputStream bufferedStream = new BufferedInputStream(new FileInputStream(file))) {
                mime = URLConnection.guessContentTypeFromStream(bufferedStream);
            }
        }
        if (mime == null) {
            return defaultMimeType;
        }
        return mime;
    }

    public static StreamDetails createStreamDetailsFromFile(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        final var size = file.length();
        final var mime = getFileMimeType(file, "application/octet-stream");
        return new StreamDetails(stream, mime, size);
    }

    public static Fingerprint computeSafetyNumber(
            boolean isUuidCapable,
            SignalServiceAddress ownAddress,
            IdentityKey ownIdentityKey,
            SignalServiceAddress theirAddress,
            IdentityKey theirIdentityKey
    ) {
        int version;
        byte[] ownId;
        byte[] theirId;

        if (isUuidCapable) {
            // Version 2: UUID user
            version = 2;
            ownId = UuidUtil.toByteArray(ownAddress.getUuid());
            theirId = UuidUtil.toByteArray(theirAddress.getUuid());
        } else {
            // Version 1: E164 user
            version = 1;
            if (!ownAddress.getNumber().isPresent() || !theirAddress.getNumber().isPresent()) {
                return null;
            }
            ownId = ownAddress.getNumber().get().getBytes();
            theirId = theirAddress.getNumber().get().getBytes();
        }

        return new NumericFingerprintGenerator(5200).createFor(version,
                ownId,
                ownIdentityKey,
                theirId,
                theirIdentityKey);
    }
}
