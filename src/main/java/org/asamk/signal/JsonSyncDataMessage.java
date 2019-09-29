package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

import java.util.ArrayList;
import java.util.List;

class JsonSyncDataMessage extends JsonDataMessage {

    String destination;

    JsonSyncDataMessage(SentTranscriptMessage transcriptMessage) {
        super(transcriptMessage.getMessage());
        if (transcriptMessage.getDestination().isPresent()) {
            this.destination = transcriptMessage.getDestination().get();
        }
    }
}
