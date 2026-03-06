package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@JsonSchema(title = "SyncStoryMessage")
record JsonSyncStoryMessage(
        @JsonProperty(required = true) String destinationNumber,
        @JsonProperty(required = true) String destinationUuid,
        @JsonProperty(required = true) @JsonUnwrapped JsonStoryMessage dataMessage
) {

    static JsonSyncStoryMessage from(MessageEnvelope.Sync.Sent transcriptMessage) {
        if (transcriptMessage.destination().isPresent()) {
            final var address = transcriptMessage.destination().get();
            return new JsonSyncStoryMessage(address.number().orElse(null),
                    address.uuid().map(UUID::toString).orElse(null),
                    transcriptMessage.story().map(JsonStoryMessage::from).orElse(null));

        } else {
            return new JsonSyncStoryMessage(null,
                    null,
                    transcriptMessage.story().map(JsonStoryMessage::from).orElse(null));
        }
    }
}
