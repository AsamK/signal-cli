package org.asamk.signal.manager.util;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Utils {

    private final static Logger logger = LoggerFactory.getLogger(Utils.class);

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

    private static boolean isBase64DataString(final String[] parts) {
        return parts.length == 2
                && parts[0].startsWith("data:")
                && parts[0].contains("/")
                && parts[1].startsWith("base64,");
    }

    public static boolean isBase64DataString(final String value) {
        return isBase64DataString(value.split(";", 2));
    }

    public static StreamDetails createStreamDetailsFromBase64(final String base64) {
        final String[] parts = base64.split(";", 2);
        if (!isBase64DataString(parts)) {
            throw new IllegalArgumentException("The given argument is not a valid base64 string.");
        }

        parts[0] = parts[0].substring(5);
        final byte[] bytes = Base64.getDecoder().decode(parts[1].substring(7).getBytes(StandardCharsets.UTF_8));

        return new StreamDetails(new ByteArrayInputStream(bytes), parts[0], bytes.length);
    }

    public static StreamDetails createStreamDetailsFromFile(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        final var size = file.length();
        final var mime = getFileMimeType(file, "application/octet-stream");
        return new StreamDetails(stream, mime, size);
    }

    public static StreamDetails createStreamDetails(final String value) throws IOException {
        if (isBase64DataString(value)) {
            return createStreamDetailsFromBase64(value);
        }

        return createStreamDetailsFromFile(new File(value));
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
            ownId = ownAddress.getServiceId().toByteArray();
            theirId = theirAddress.getServiceId().toByteArray();
        } else {
            // Version 1: E164 user
            version = 1;
            if (ownAddress.getNumber().isEmpty() || theirAddress.getNumber().isEmpty()) {
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

    public static Locale getDefaultLocale(Locale fallback) {
        final var locale = Locale.getDefault();
        if (locale == null) {
            return fallback;
        }
        try {
            Locale.LanguageRange.parse(locale.getLanguage() + "-" + locale.getCountry());
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid locale, ignoring: {}", locale);
            return fallback;
        }

        return locale;
    }

    public static <L, R, T> Stream<T> zip(Stream<L> leftStream, Stream<R> rightStream, BiFunction<L, R, T> combiner) {
        Spliterator<L> lefts = leftStream.spliterator();
        Spliterator<R> rights = rightStream.spliterator();
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(Long.min(lefts.estimateSize(),
                rights.estimateSize()), lefts.characteristics() & rights.characteristics()) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                return lefts.tryAdvance(left -> rights.tryAdvance(right -> action.accept(combiner.apply(left, right))));
            }
        }, leftStream.isParallel() || rightStream.isParallel());
    }

    public static Map<String, String> getQueryMap(String query) {
        var params = query.split("&");
        var map = new HashMap<String, String>();
        for (var param : params) {
            final var paramParts = param.split("=");
            var name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            var value = URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }
}
