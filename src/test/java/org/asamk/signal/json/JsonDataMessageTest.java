package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDataMessageTest {

    @Test
    void serializesProtocolMetadata() {
        final var dataMessage = dataMessage(Optional.of(new MessageEnvelope.Data.GroupCallUpdate("era-id")),
                true,
                true,
                true);

        final var json = Util.createJsonObjectMapper().valueToTree(JsonDataMessage.from(dataMessage, null));

        assertEquals("era-id", json.get("groupCallUpdate").get("eraId").textValue());
        assertTrue(json.get("isEndSession").booleanValue());
        assertTrue(json.get("isProfileKeyUpdate").booleanValue());
        assertTrue(json.get("hasProfileKey").booleanValue());
    }

    @Test
    void omitsAbsentGroupCallUpdateAndSerializesFalseFlags() {
        final var dataMessage = dataMessage(Optional.empty(), false, false, false);

        final var json = Util.createJsonObjectMapper().valueToTree(JsonDataMessage.from(dataMessage, null));

        assertFalse(json.has("groupCallUpdate"));
        assertFalse(json.get("isEndSession").booleanValue());
        assertFalse(json.get("isProfileKeyUpdate").booleanValue());
        assertFalse(json.get("hasProfileKey").booleanValue());
    }

    private static MessageEnvelope.Data dataMessage(
            final Optional<MessageEnvelope.Data.GroupCallUpdate> groupCallUpdate,
            final boolean isEndSession,
            final boolean isProfileKeyUpdate,
            final boolean hasProfileKey
    ) {
        return new MessageEnvelope.Data(1L,
                Optional.empty(),
                Optional.empty(),
                groupCallUpdate,
                Optional.empty(),
                0,
                false,
                false,
                isEndSession,
                isProfileKeyUpdate,
                hasProfileKey,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
