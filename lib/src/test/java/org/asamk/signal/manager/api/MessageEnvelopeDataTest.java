package org.asamk.signal.manager.api;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.asamk.signal.manager.storage.recipients.TestRecipientIdFactory;
import org.junit.jupiter.api.Test;
import org.signal.core.models.ServiceId;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MessageEnvelope.Data#from} null-safety handling.
 *
 * <p>Signal Desktop v8.0.0 switched to binary ACI encoding in protobuf messages.
 * When the binary ACI field cannot be parsed, the library falls back to
 * {@link ACI#UNKNOWN} (isValid=false, isUnknown=true). The MessageEnvelope
 * construction must handle this gracefully — preserving valuable content like
 * quote text while falling back to UNKNOWN_UUID for unresolvable authors.</p>
 */
class MessageEnvelopeDataTest {

    static final ACI ACI_A = ACI.from(UUID.randomUUID());
    static final ACI ACI_B = ACI.from(UUID.randomUUID());

    static final Map<ServiceId, Long> RECIPIENT_IDS = new HashMap<>();
    static long nextId = 1;

    static long getRecipientId(ServiceId serviceId) {
        return RECIPIENT_IDS.computeIfAbsent(serviceId, k -> nextId++);
    }

    static final RecipientResolver RECIPIENT_RESOLVER = new RecipientResolver() {
        @Override
        public org.asamk.signal.manager.storage.recipients.RecipientId resolveRecipient(RecipientAddress address) {
            return TestRecipientIdFactory.create(address.serviceId().map(s -> getRecipientId(s)).orElse(0L));
        }

        @Override
        public org.asamk.signal.manager.storage.recipients.RecipientId resolveRecipient(long recipientId) {
            return TestRecipientIdFactory.create(recipientId);
        }

        @Override
        public org.asamk.signal.manager.storage.recipients.RecipientId resolveRecipient(String identifier) {
            return TestRecipientIdFactory.create(identifier.hashCode());
        }

        @Override
        public org.asamk.signal.manager.storage.recipients.RecipientId resolveRecipient(ServiceId serviceId) {
            return TestRecipientIdFactory.create(getRecipientId(serviceId));
        }
    };

    static final RecipientAddressResolver ADDRESS_RESOLVER =
            recipientId -> new RecipientAddress(ACI_A);

    static final MessageEnvelope.AttachmentFileProvider FILE_PROVIDER = pointer -> new File("/dev/null");

    // --- Quote tests ---

    @Test
    void quoteWithValidAuthor_isPreserved() {
        var quote = new SignalServiceDataMessage.Quote(
                1000L, ACI_A, "Hello world", List.of(), List.of(),
                SignalServiceDataMessage.Quote.Type.NORMAL, List.of());
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withQuote(quote)
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.quote().isPresent(), "Quote should be present");
        assertEquals("Hello world", data.quote().get().text().orElse(null));
        assertNotNull(data.quote().get().author(), "Quote author should not be null");
        assertTrue(data.quote().get().author().uuid().isPresent(), "Quote author should have a UUID");
    }

    @Test
    void quoteWithUnknownAuthor_isPreservedWithFallback() {
        // ACI.UNKNOWN is what the library produces when binary ACI parsing fails.
        // The old filter (.filter(q -> q.getAuthor() != null && q.getAuthor().isValid()))
        // would drop the entire quote because ACI.UNKNOWN.isValid() == false.
        var quote = new SignalServiceDataMessage.Quote(
                1000L, ACI.UNKNOWN, "Hello world", List.of(), List.of(),
                SignalServiceDataMessage.Quote.Type.NORMAL, List.of());
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withQuote(quote)
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.quote().isPresent(), "Quote with unknown author should still be present");
        assertEquals("Hello world", data.quote().get().text().orElse(null),
                "Quote text should be preserved even with unknown author");
        assertNotNull(data.quote().get().author(), "Quote should have a fallback author");
        assertEquals(org.asamk.signal.manager.api.RecipientAddress.UNKNOWN_UUID,
                data.quote().get().author().uuid().orElse(null),
                "Quote author should fall back to UNKNOWN_UUID");
    }

    @Test
    void quoteWithEmptyText_handledDefensively() {
        var quote = new SignalServiceDataMessage.Quote(
                1000L, ACI_A, "", List.of(), List.of(),
                SignalServiceDataMessage.Quote.Type.NORMAL, List.of());
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withQuote(quote)
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.quote().isPresent(), "Quote should be present even with empty text");
    }

    // --- Reaction tests ---

    @Test
    void reactionWithValidAuthor_isPreserved() {
        var reaction = new SignalServiceDataMessage.Reaction("👍", false, ACI_A, 1000L);
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withReaction(reaction)
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.reaction().isPresent(), "Reaction should be present");
        assertEquals("👍", data.reaction().get().emoji());
        assertEquals(1000L, data.reaction().get().targetSentTimestamp());
    }

    @Test
    void reactionWithUnknownAuthor_isPreserved() {
        // ACI.UNKNOWN is valid enough to resolve — it's not null. The reaction
        // should still come through since the emoji and timestamp are useful.
        var reaction = new SignalServiceDataMessage.Reaction("❤️", false, ACI.UNKNOWN, 2000L);
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withReaction(reaction)
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.reaction().isPresent(), "Reaction with UNKNOWN author should be present");
        assertEquals("❤️", data.reaction().get().emoji());
    }

    // --- Mention tests ---

    @Test
    void mentionWithValidServiceId_isPreserved() {
        var mention = new SignalServiceDataMessage.Mention(ACI_A, 0, 1);
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withBody("\uFFFC hello")
                .withMentions(List.of(mention))
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertEquals(1, data.mentions().size(), "Valid mention should be preserved");
        assertEquals(0, data.mentions().getFirst().start());
        assertEquals(1, data.mentions().getFirst().length());
    }

    @Test
    void multipleMentions_allPreserved() {
        var mention1 = new SignalServiceDataMessage.Mention(ACI_A, 0, 1);
        var mention2 = new SignalServiceDataMessage.Mention(ACI_B, 5, 1);
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withBody("\uFFFC hey \uFFFC")
                .withMentions(List.of(mention1, mention2))
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertEquals(2, data.mentions().size(), "Both valid mentions should be preserved");
        assertEquals(0, data.mentions().get(0).start());
        assertEquals(5, data.mentions().get(1).start());
    }

    @Test
    void noMentions_returnsEmptyList() {
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withBody("No mentions here")
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.mentions().isEmpty(), "No mentions should result in empty list");
    }

    // --- Combined tests ---

    @Test
    void messageWithBodyAndNoOptionalFields_works() {
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withBody("Just a plain message")
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertEquals("Just a plain message", data.body().orElse(null));
        assertFalse(data.reaction().isPresent());
        assertFalse(data.quote().isPresent());
        assertTrue(data.mentions().isEmpty());
    }

    @Test
    void messageWithQuoteAndMentions_bothPreserved() {
        var quote = new SignalServiceDataMessage.Quote(
                1000L, ACI_B, "Original text", List.of(), List.of(),
                SignalServiceDataMessage.Quote.Type.NORMAL, List.of());
        var mention = new SignalServiceDataMessage.Mention(ACI_A, 0, 1);
        var dataMessage = SignalServiceDataMessage.newBuilder()
                .withTimestamp(System.currentTimeMillis())
                .withBody("\uFFFC I agree!")
                .withQuote(quote)
                .withMentions(List.of(mention))
                .build();

        var data = MessageEnvelope.Data.from(dataMessage, RECIPIENT_RESOLVER, ADDRESS_RESOLVER, FILE_PROVIDER);

        assertTrue(data.quote().isPresent(), "Quote should be present");
        assertEquals("Original text", data.quote().get().text().orElse(null));
        assertEquals(1, data.mentions().size(), "Mention should be present");
    }
}
