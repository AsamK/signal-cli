package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientAddress;

import java.util.UUID;

record JsonSyncDataMessage(
        @Deprecated String destination,
        String destinationNumber,
        String destinationUuid,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonEditMessage editMessage,
        @JsonUnwrapped JsonDataMessage dataMessage
) {

    static JsonSyncDataMessage from(MessageEnvelope.Sync.Sent transcriptMessage, Manager m) {
        return new JsonSyncDataMessage(transcriptMessage.destination()
                .map(RecipientAddress::getLegacyIdentifier)
                .orElse(null),
                transcriptMessage.destination().flatMap(RecipientAddress::number).orElse(null),
                transcriptMessage.destination().flatMap(address -> address.uuid().map(UUID::toString)).orElse(null),
                transcriptMessage.editMessage()
                    .map(data -> JsonEditMessage.from(data, m))
                    .orElse(null),
                transcriptMessage.message()
                    .map(data -> JsonDataMessage.from(data, m))
                    .orElse(null));
    }
}
