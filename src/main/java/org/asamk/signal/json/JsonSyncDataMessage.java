package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

class JsonSyncDataMessage extends JsonDataMessage {

    @JsonProperty
    @Deprecated
    final String destination;

    @JsonProperty
    final String destinationNumber;

    @JsonProperty
    final String destinationUuid;

    JsonSyncDataMessage(SentTranscriptMessage transcriptMessage, Manager m) {
        super(transcriptMessage.getMessage(), m);

        if (transcriptMessage.getDestination().isPresent()) {
            final var address = transcriptMessage.getDestination().get();
            this.destination = getLegacyIdentifier(address);
            this.destinationNumber = address.getNumber().orNull();
            this.destinationUuid = address.getUuid().toString();
        } else {
            this.destination = null;
            this.destinationNumber = null;
            this.destinationUuid = null;
        }
    }

    JsonSyncDataMessage(Signal.SyncMessageReceived messageReceived) {
        super(messageReceived);
        this.destination = messageReceived.getDestination();
        this.destinationNumber = null;
        this.destinationUuid = null;
    }
}
