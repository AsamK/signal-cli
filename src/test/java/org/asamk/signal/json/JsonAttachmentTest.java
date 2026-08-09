package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAttachmentTest {

    @Test
    void serializesVoiceNoteFlag() {
        assertVoiceNoteFlag(true);
    }

    @Test
    void serializesNonVoiceNoteFlag() {
        assertVoiceNoteFlag(false);
    }

    private static void assertVoiceNoteFlag(final boolean isVoiceNote) {
        final var attachment = new MessageEnvelope.Data.Attachment(Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "audio/aac",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                isVoiceNote,
                false,
                false);

        final var jsonAttachment = JsonAttachment.from(attachment);
        final var json = Util.createJsonObjectMapper().valueToTree(jsonAttachment);

        assertEquals(isVoiceNote, jsonAttachment.isVoiceNote());
        assertTrue(json.has("isVoiceNote"));
        assertEquals(isVoiceNote, json.get("isVoiceNote").booleanValue());
    }
}
