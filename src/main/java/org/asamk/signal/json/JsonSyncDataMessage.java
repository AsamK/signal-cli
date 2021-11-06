package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

record JsonSyncDataMessage(
        @Deprecated String destination,
        String destinationNumber,
        String destinationUuid,
        @JsonUnwrapped JsonDataMessage dataMessage
) {

    static JsonSyncDataMessage from(MessageEnvelope.Sync.Sent transcriptMessage) {
        if (transcriptMessage.destination().isPresent()) {
            final var address = transcriptMessage.destination().get();
            return new JsonSyncDataMessage(address.getLegacyIdentifier(),
                    address.getNumber().orElse(null),
                    address.getUuid().map(UUID::toString).orElse(null),
                    JsonDataMessage.from(transcriptMessage.message()));

        } else {
            return new JsonSyncDataMessage(null, null, null, JsonDataMessage.from(transcriptMessage.message()));
        }
    }
}
