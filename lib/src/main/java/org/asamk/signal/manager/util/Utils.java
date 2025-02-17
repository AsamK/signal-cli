package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.Pair;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.NetworkResult;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import okio.ByteString;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static Pair<StreamDetails, Optional<String>> createStreamDetailsFromDataURI(final String dataURI) {
        final DataURI uri = DataURI.of(dataURI);

        return new Pair<>(new StreamDetails(new ByteArrayInputStream(uri.data()), uri.mediaType(), uri.data().length),
                Optional.ofNullable(uri.parameter().get("filename")));
    }

    public static StreamDetails createStreamDetailsFromFile(final File file) throws IOException {
        final InputStream stream = new FileInputStream(file);
        final var size = file.length();
        final var mime = MimeUtils.getFileMimeType(file).orElse(MimeUtils.OCTET_STREAM);
        return new StreamDetails(stream, mime, size);
    }

    public static Pair<StreamDetails, Optional<String>> createStreamDetails(final String value) throws IOException {
        try {
            return createStreamDetailsFromDataURI(value);
        } catch (final IllegalArgumentException e) {
            final File f = new File(value);

            return new Pair<>(createStreamDetailsFromFile(f), Optional.of(f.getName()));
        }
    }

    public static Fingerprint computeSafetyNumberForNumber(
            String ownNumber,
            IdentityKey ownIdentityKey,
            String theirNumber,
            IdentityKey theirIdentityKey
    ) {
        // Version 1: E164 user
        final var version = 1;
        final var ownId = ownNumber.getBytes(StandardCharsets.UTF_8);
        final var theirId = theirNumber.getBytes(StandardCharsets.UTF_8);

        return getFingerprint(version, ownId, ownIdentityKey, theirId, theirIdentityKey);
    }

    public static Fingerprint computeSafetyNumberForUuid(
            ServiceId ownServiceId,
            IdentityKey ownIdentityKey,
            ServiceId theirServiceId,
            IdentityKey theirIdentityKey
    ) {
        // Version 2: UUID user
        final var version = 2;
        final var ownId = ownServiceId.toByteArray();
        final var theirId = theirServiceId.toByteArray();

        return getFingerprint(version, ownId, ownIdentityKey, theirId, theirIdentityKey);
    }

    private static Fingerprint getFingerprint(
            final int version,
            final byte[] ownId,
            final IdentityKey ownIdentityKey,
            final byte[] theirId,
            final IdentityKey theirIdentityKey
    ) {
        return new NumericFingerprintGenerator(5200).createFor(version,
                ownId,
                ownIdentityKey,
                theirId,
                theirIdentityKey);
    }

    public static Locale getDefaultLocale(Locale fallback) {
        final var locale = Locale.getDefault();
        if (locale == null) {
            logger.debug("No default locale found, using fallback: {}", fallback);
            return fallback;
        }
        final var localeString = locale.getLanguage() + "-" + locale.getCountry();
        try {
            Locale.LanguageRange.parse(localeString);
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid locale '{}', using fallback: {}", locale, fallback);
            return fallback;
        }

        logger.trace("Using default locale: {} ({})", locale, localeString);
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

    public static <OK, NK, V> Map<NK, V> mapKeys(Map<OK, V> map, Function<OK, NK> keyMapper) {
        return map.entrySet().stream().collect(Collectors.toMap(e -> keyMapper.apply(e.getKey()), Map.Entry::getValue));
    }

    public static Map<String, String> getQueryMap(String query) {
        var params = query.split("&");
        var map = new HashMap<String, String>();
        for (var param : params) {
            final var paramParts = param.split("=");
            var name = URLDecoder.decode(paramParts[0], StandardCharsets.UTF_8);
            var value = paramParts.length == 1 ? null : URLDecoder.decode(paramParts[1], StandardCharsets.UTF_8);
            map.put(name, value);
        }
        return map;
    }

    public static <T> T handleResponseException(final NetworkResult<T> response) throws IOException {
        final var throwableOptional = response.getCause();
        if (throwableOptional != null) {
            if (throwableOptional instanceof IOException ioException) {
                throw ioException;
            } else {
                throw new IOException(throwableOptional);
            }
        }
        return response.successOrThrow();
    }

    public static ByteString firstNonEmpty(ByteString... strings) {
        for (final var s : strings) {
            if (s.size() > 0) {
                return s;
            }
        }
        return ByteString.EMPTY;
    }

    @SafeVarargs
    public static <T> List<T> firstNonEmpty(List<T>... values) {
        for (final var s : values) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return List.of();
    }

    public static String firstNonEmpty(String... strings) {
        for (final var s : strings) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return "";
    }

    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        for (final var v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static String nullIfEmpty(String string) {
        return string == null || string.isEmpty() ? null : string;
    }

    public static Proxy getHttpsProxy() {
        final URI uri;
        try {
            uri = new URI("https://example");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        final var proxies = ProxySelector.getDefault().select(uri);
        if (proxies.isEmpty()) {
            return Proxy.NO_PROXY;
        } else {
            return proxies.getFirst();
        }
    }
}
