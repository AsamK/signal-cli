package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

record JsonSyncDataMessage(
        @Deprecated String destination,
        String destinationNumber,
        String destinationUuid,
        @JsonUnwrapped JsonDataMessage dataMessage
) {

    static JsonSyncDataMessage from(SentTranscriptMessage transcriptMessage, Manager m) {
        if (transcriptMessage.getDestination().isPresent()) {
            final var address = transcriptMessage.getDestination().get();
            return new JsonSyncDataMessage(getLegacyIdentifier(address),
                    address.getNumber().orNull(),
                    address.getUuid().toString(),
                    JsonDataMessage.from(transcriptMessage.getMessage(), m));

        } else {
            return new JsonSyncDataMessage(null, null, null, JsonDataMessage.from(transcriptMessage.getMessage(), m));
        }
    }

    static JsonSyncDataMessage from(Signal.SyncMessageReceived messageReceived) {
        return new JsonSyncDataMessage(messageReceived.getDestination(),
                null,
                null,
                JsonDataMessage.from(messageReceived));
    }
}
