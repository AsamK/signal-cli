package org.asamk.signal.manager.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Optional;

public class MimeUtils {

    public static final String LONG_TEXT = "text/x-signal-plain";
    public static final String PLAIN_TEXT = "text/plain";
    public static final String OCTET_STREAM = "application/octet-stream";

    public static Optional<String> getFileMimeType(final File file) throws IOException {
        var mime = Files.probeContentType(file.toPath());
        if (mime != null) {
            return Optional.of(mime);
        }

        try (final InputStream bufferedStream = new BufferedInputStream(new FileInputStream(file))) {
            return getStreamMimeType(bufferedStream);
        }
    }

    public static Optional<String> getStreamMimeType(final InputStream inputStream) throws IOException {
        return Optional.ofNullable(URLConnection.guessContentTypeFromStream(inputStream));
    }

    public static Optional<String> guessExtensionFromMimeType(String mimeType) {
        return Optional.ofNullable(switch (mimeType) {
            case "application/vnd.android.package-archive" -> "apk";
            case "application/json" -> "json";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/heic" -> "heic";
            case "image/heif" -> "heif";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "audio/aac" -> "aac";
            case "video/mp4" -> "mp4";
            case "text/x-vcard" -> "vcf";
            case PLAIN_TEXT, LONG_TEXT -> "txt";
            case OCTET_STREAM -> "bin";
            default -> null;
        });
    }
}
