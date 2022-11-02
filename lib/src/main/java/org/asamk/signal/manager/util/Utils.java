package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Utils {

    private final static Logger logger = LoggerFactory.getLogger(Utils.class);

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

    public static Fingerprint computeSafetyNumber(
            boolean isUuidCapable,
            RecipientAddress ownAddress,
            IdentityKey ownIdentityKey,
            RecipientAddress theirAddress,
            IdentityKey theirIdentityKey
    ) {
        int version;
        byte[] ownId;
        byte[] theirId;

        if (!isUuidCapable && ownAddress.number().isPresent() && theirAddress.number().isPresent()) {
            // Version 1: E164 user
            version = 1;
            ownId = ownAddress.number().get().getBytes();
            theirId = theirAddress.number().get().getBytes();
        } else if (isUuidCapable && theirAddress.serviceId().isPresent()) {
            // Version 2: UUID user
            version = 2;
            ownId = ownAddress.getServiceId().toByteArray();
            theirId = theirAddress.getServiceId().toByteArray();
        } else {
            return null;
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
}
