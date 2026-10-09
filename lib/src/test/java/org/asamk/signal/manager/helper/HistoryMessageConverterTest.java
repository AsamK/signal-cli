package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.RecipientAddress;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryMessageConverterTest {

    @Test
    void convertsIncomingAndOutgoingStandardMessagesInOrder() throws Exception {
        final var self = UUID.fromString("11111111-1111-4111-8111-111111111111");
        final var contact = UUID.fromString("22222222-2222-4222-8222-222222222222");
        final var converter = new HistoryMessageConverter(new RecipientAddress(self));

        converter.accept("{\"recipient\":{\"id\":\"1\",\"self\":{}}}");
        converter.accept("{\"recipient\":{\"id\":\"2\",\"contact\":{\"aci\":\""
                + uuidBase64(contact)
                + "\",\"e164\":\"15551234567\"}}}");
        converter.accept("{\"chat\":{\"id\":\"3\",\"recipientId\":\"2\"}}");

        final var incoming = converter.accept("{\"chatItem\":{\"chatId\":\"3\",\"authorId\":\"2\","
                + "\"dateSent\":\"100\",\"incoming\":{\"dateReceived\":\"102\",\"dateServerSent\":\"101\","
                + "\"sealedSender\":true},\"standardMessage\":{\"text\":{\"body\":\"hello\"}}}}");
        assertFalse(incoming.skipped());
        assertNotNull(incoming.envelope());
        assertEquals(contact.toString(), incoming.envelope().sourceAddress().orElseThrow().aci().orElseThrow());
        assertEquals("hello", incoming.envelope().data().orElseThrow().body().orElseThrow());
        assertEquals(102, incoming.envelope().serverDeliveredTimestamp());

        final var outgoing = converter.accept("{\"chatItem\":{\"chatId\":\"3\",\"authorId\":\"1\","
                + "\"dateSent\":\"200\",\"outgoing\":{},\"standardMessage\":{\"text\":{\"body\":\"bye\"}}}}");
        assertFalse(outgoing.skipped());
        final var sent = outgoing.envelope().sync().orElseThrow().sent().orElseThrow();
        assertEquals("+15551234567", sent.destination().orElseThrow().number().orElseThrow());
        assertEquals("bye", sent.message().orElseThrow().body().orElseThrow());
    }

    @Test
    void countsUnsupportedChatItemsWithoutTreatingMetadataAsSkipped() throws Exception {
        final var converter = new HistoryMessageConverter(new RecipientAddress(
                UUID.fromString("11111111-1111-4111-8111-111111111111")));
        assertFalse(converter.accept("{\"account\":{}}").skipped());
        assertTrue(converter.accept("{\"chatItem\":{\"updateMessage\":{}}}").skipped());
        assertTrue(converter.accept("{\"sticker\":{}}").skipped());
    }

    @Test
    void mapsExporterAttachmentFieldsThroughTypedBoundary() throws Exception {
        final var contact = UUID.fromString("22222222-2222-4222-8222-222222222222");
        final var converter = new HistoryMessageConverter(new RecipientAddress(
                UUID.fromString("11111111-1111-4111-8111-111111111111")));
        converter.accept("{\"recipient\":{\"id\":\"2\",\"contact\":{\"aci\":\""
                + uuidBase64(contact)
                + "\"}}}");
        converter.accept("{\"chat\":{\"id\":\"3\",\"recipientId\":\"2\"}}");

        final var result = converter.accept("{\"chatItem\":{\"chatId\":\"3\",\"authorId\":\"2\","
                + "\"dateSent\":\"100\",\"incoming\":{},\"standardMessage\":{\"attachments\":[{"
                + "\"flag\":\"VOICE_MESSAGE\",\"pointer\":{\"fileName\":\"voice.ogg\","
                + "\"contentType\":\"audio/ogg\",\"caption\":\"memo\",\"width\":12,\"height\":34,"
                + "\"locatorInfo\":{\"transitCdnKey\":\"cdn-key\",\"size\":99,"
                + "\"transitTierUploadTimestamp\":123}}}]}}}");

        final var attachment = result.envelope().data().orElseThrow().attachments().getFirst();
        assertEquals("cdn-key", attachment.id().orElseThrow());
        assertEquals("voice.ogg", attachment.fileName().orElseThrow());
        assertEquals("audio/ogg", attachment.contentType());
        assertEquals(99, attachment.size().orElseThrow());
        assertEquals(123, attachment.uploadTimestamp().orElseThrow());
        assertEquals("memo", attachment.caption().orElseThrow());
        assertEquals(12, attachment.width().orElseThrow());
        assertEquals(34, attachment.height().orElseThrow());
        assertTrue(attachment.isVoiceNote());
    }

    private static String uuidBase64(final UUID uuid) {
        final var buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}
