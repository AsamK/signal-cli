package org.asamk.signal.manager.util;

import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.api.util.StreamDetails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentUtilsTest {

    @Test
    public void createAttachmentStream_setsWidthAndHeightForImage() throws Exception {
        final var imageBytes = pngBytes(37, 21);
        final var streamDetails = new StreamDetails(new ByteArrayInputStream(imageBytes), "image/png", imageBytes.length);

        final var attachment = AttachmentUtils.createAttachmentStream(streamDetails, Optional.of("meme.png"), null);

        assertEquals(37, attachment.getWidth());
        assertEquals(21, attachment.getHeight());
        assertArrayEquals(imageBytes, attachment.getInputStream().readAllBytes());
    }

    @Test
    public void createAttachmentStream_leavesWidthAndHeightZeroForNonImage() throws Exception {
        final var bytes = "not an image".getBytes();
        final var streamDetails = new StreamDetails(new ByteArrayInputStream(bytes), "application/octet-stream", bytes.length);

        final var attachment = AttachmentUtils.createAttachmentStream(streamDetails, Optional.of("file.bin"), null);

        assertEquals(0, attachment.getWidth());
        assertEquals(0, attachment.getHeight());
        assertArrayEquals(bytes, attachment.getInputStream().readAllBytes());
    }

    private static byte[] pngBytes(final int width, final int height) throws Exception {
        final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
