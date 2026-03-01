package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "SyncStoryMessage")
record JsonSyncStoryMessage(
        String destinationNumber, String destinationUuid, @JsonUnwrapped JsonStoryMessage dataMessage
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
