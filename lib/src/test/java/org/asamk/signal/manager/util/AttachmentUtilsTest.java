package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.Message.AttachmentDimensions;
import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentUtilsTest {

    @Test
    public void createAttachmentStream_setsWidthAndHeightForImage() throws Exception {
        final var imageBytes = pngBytes(37, 21);
        final var streamDetails = new StreamDetails(new ByteArrayInputStream(imageBytes),
                "image/png",
                imageBytes.length);

        final var attachment = AttachmentUtils.createAttachmentStream(streamDetails, Optional.of("meme.png"), null);

        assertEquals(37, attachment.getWidth());
        assertEquals(21, attachment.getHeight());
        assertArrayEquals(imageBytes, attachment.getInputStream().readAllBytes());
    }

    @Test
    public void createAttachmentStream_leavesWidthAndHeightZeroForNonImage() throws Exception {
        final var bytes = "not an image".getBytes();
        final var streamDetails = new StreamDetails(new ByteArrayInputStream(bytes),
                "application/octet-stream",
                bytes.length);

        final var attachment = AttachmentUtils.createAttachmentStream(streamDetails, Optional.of("file.bin"), null);

        assertEquals(0, attachment.getWidth());
        assertEquals(0, attachment.getHeight());
        assertArrayEquals(bytes, attachment.getInputStream().readAllBytes());
    }

    @Test
    public void createAttachmentStream_setsSuppliedDimensionsAndBlurHash() throws Exception {
        final var bytes = "opaque video bytes".getBytes();
        final var blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj";
        final var details = new StreamDetails(new ByteArrayInputStream(bytes), "video/mp4", bytes.length);
        final var attachment = AttachmentUtils.createAttachmentStream(details,
                Optional.of("clip.mp4"), false, new AttachmentDimensions(1080, 1920), blurHash, null);

        assertEquals(1080, attachment.getWidth());
        assertEquals(1920, attachment.getHeight());
        assertEquals(Optional.of(blurHash), attachment.getBlurHash());
        assertArrayEquals(bytes, attachment.getInputStream().readAllBytes());
    }

    @Test
    public void createAttachmentStream_skipsProbingWhenDimensionsSupplied() throws Exception {
        final var imageBytes = pngBytes(37, 21);
        final var stream = new ByteArrayInputStream(imageBytes);
        final var details = new StreamDetails(stream, "image/png", imageBytes.length);
        final var attachment = AttachmentUtils.createAttachmentStream(details,
                Optional.of("meme.png"), false, new AttachmentDimensions(100, 200), null, null);

        assertEquals(imageBytes.length, stream.available());
        assertEquals(100, attachment.getWidth());
        assertEquals(200, attachment.getHeight());
        assertArrayEquals(imageBytes, attachment.getInputStream().readAllBytes());
    }

    private static byte[] pngBytes(final int width, final int height) throws Exception {
        final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
