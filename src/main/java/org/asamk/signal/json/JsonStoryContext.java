package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "StoryContext")
record JsonStoryContext(
        String authorNumber, String authorUuid, long sentTimestamp
) {

    static JsonStoryContext from(MessageEnvelope.Data.StoryContext storyContext) {
        return new JsonStoryContext(storyContext.author().number().orElse(null),
                storyContext.author().uuid().map(UUID::toString).orElse(null),
                storyContext.sentTimestamp());
    }
}
