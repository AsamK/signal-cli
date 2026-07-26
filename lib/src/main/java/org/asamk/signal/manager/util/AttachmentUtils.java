package org.asamk.signal.manager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public class AttachmentUtils {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentUtils.class);

    // Images are fully buffered in memory to probe their dimensions, so cap how large a file we'll do this for.
    private static final long MAX_DIMENSION_PROBE_SIZE = 20 * 1024 * 1024;

    public static SignalServiceAttachmentStream createAttachmentStream(
            StreamDetails streamDetails,
            Optional<String> name,
            boolean voiceNote,
            ResumableUploadSpec resumableUploadSpec
    ) throws ResumeLocationInvalidException, IOException {
        final var uploadTimestamp = System.currentTimeMillis();
        final var probedStream = probeImageDimensions(streamDetails);
        return SignalServiceAttachmentStream.newStreamBuilder()
                .withStream(probedStream.inputStream())
                .withContentType(streamDetails.getContentType())
                .withLength(streamDetails.getLength())
                .withFileName(name.orElse(null))
                .withVoiceNote(voiceNote)
                .withWidth(probedStream.width())
                .withHeight(probedStream.height())
                .withUploadTimestamp(uploadTimestamp)
                .withResumableUploadSpec(resumableUploadSpec)
                .withUuid(UUID.randomUUID())
                .build();
    }

    public static SignalServiceAttachmentStream createAttachmentStream(
            StreamDetails streamDetails,
            Optional<String> name,
            ResumableUploadSpec resumableUploadSpec
    ) throws ResumeLocationInvalidException, IOException {
        return createAttachmentStream(streamDetails, name, false, resumableUploadSpec);
    }

    /**
     * Reads the attachment's dimensions if it's an image, so recipients don't get a square-cropped thumbnail.
     * Falls back to width/height 0 (today's behavior) if the content isn't an image, is too large to probe
     * cheaply, or fails to parse.
     */
    private static ProbedStream probeImageDimensions(StreamDetails streamDetails) throws IOException {
        final var contentType = streamDetails.getContentType();
        final var length = streamDetails.getLength();
        if (contentType == null || !contentType.startsWith("image/") || length <= 0 || length > MAX_DIMENSION_PROBE_SIZE) {
            return new ProbedStream(streamDetails.getStream(), 0, 0);
        }

        final var bytes = streamDetails.getStream().readAllBytes();
        var width = 0;
        var height = 0;
        try {
            final var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (IOException e) {
            logger.debug("Failed to probe image dimensions, sending without width/height: {}", e.getMessage());
        }
        return new ProbedStream(new ByteArrayInputStream(bytes), width, height);
    }

    private record ProbedStream(InputStream inputStream, int width, int height) {}
}
