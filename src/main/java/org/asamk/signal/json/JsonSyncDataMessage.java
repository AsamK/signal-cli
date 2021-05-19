package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

class JsonSyncDataMessage extends JsonDataMessage {

    @JsonProperty
    final String destination;

    JsonSyncDataMessage(SentTranscriptMessage transcriptMessage, Manager m) {
        super(transcriptMessage.getMessage(), m);

        this.destination = transcriptMessage.getDestination().transform(Util::getLegacyIdentifier).orNull();
    }

    JsonSyncDataMessage(Signal.SyncMessageReceived messageReceived) {
        super(messageReceived);
        destination = messageReceived.getDestination();
    }
}
