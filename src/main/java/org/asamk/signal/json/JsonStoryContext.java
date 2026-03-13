package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "StoryContext")
record JsonStoryContext(
        String authorNumber, String authorUuid, long sentTimestamp
) {

    static JsonStoryContext from(MessageEnvelope.Data.StoryContext storyContext) {
        return new JsonStoryContext(storyContext.author().number().orElse(null),
                storyContext.author().uuid().map(UUID::toString).orElse(null),
                storyContext.sentTimestamp());
    }
}
