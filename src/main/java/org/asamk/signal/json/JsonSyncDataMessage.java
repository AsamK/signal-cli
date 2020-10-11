package org.asamk.signal.json;

import org.asamk.Signal;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

class JsonSyncDataMessage extends JsonDataMessage {

    String destination;

    JsonSyncDataMessage(SentTranscriptMessage transcriptMessage) {
        super(transcriptMessage.getMessage());
        if (transcriptMessage.getDestination().isPresent()) {
            this.destination = transcriptMessage.getDestination().get().getLegacyIdentifier();
        }
    }

    JsonSyncDataMessage(Signal.SyncMessageReceived messageReceived) {
        super(messageReceived);
        destination = messageReceived.getDestination();
    }
}
