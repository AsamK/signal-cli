package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

import java.util.List;

class JsonSyncMessage {

    JsonDataMessage sentMessage;
    String destination;
    List<String> blockedNumbers;
    List<ReadMessage> readMessages;

    JsonSyncMessage(SignalServiceSyncMessage syncMessage) {
        if (syncMessage.getSent().isPresent()) {
            final SentTranscriptMessage sentTranscriptMessage = syncMessage.getSent().get();
            if (sentTranscriptMessage.getDestination().isPresent()) {
                this.destination = sentTranscriptMessage.getDestination().get();
            }
            this.sentMessage = new JsonDataMessage(sentTranscriptMessage.getMessage());
        }
        if (syncMessage.getBlockedList().isPresent()) {
            this.blockedNumbers = syncMessage.getBlockedList().get().getNumbers();
        }
        if (syncMessage.getRead().isPresent()) {
            this.readMessages = syncMessage.getRead().get();
        }
    }
}
