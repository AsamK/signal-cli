package org.asamk.signal.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMessageEnvelopeTest {

    @Test
    void includesWhetherTheSenderWasUnidentified() {
        for (final var isUnidentifiedSender : List.of(true, false)) {
            final var envelope = new MessageEnvelope(Optional.empty(),
                    0,
                    1L,
                    1L,
                    1L,
                    isUnidentifiedSender,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

            final var json = new ObjectMapper().valueToTree(JsonMessageEnvelope.from(envelope,
                    null,
                    ManagerMock.create("+10000000000")));

            assertTrue(json.has("isUnidentifiedSender"));
            assertEquals(isUnidentifiedSender, json.get("isUnidentifiedSender").asBoolean());
        }
    }
}
