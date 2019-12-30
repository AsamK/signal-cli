package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

class JsonSyncDataMessage extends JsonDataMessage {

    String destination;

    JsonSyncDataMessage(SentTranscriptMessage transcriptMessage) {
        super(transcriptMessage.getMessage());
        if (transcriptMessage.getDestination().isPresent()) {
            this.destination = transcriptMessage.getDestination().get().getNumber().get();
        }
    }
}
