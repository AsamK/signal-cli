package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

import java.util.List;

class JsonSyncMessage {
    JsonDataMessage sentMessage;
    List<String> blockedNumbers;
    List<ReadMessage> readMessages;

    JsonSyncMessage(SignalServiceSyncMessage syncMessage) {
        if (syncMessage.getSent().isPresent()) {
            this.sentMessage = new JsonDataMessage(syncMessage.getSent().get().getMessage());
        }
        if (syncMessage.getBlockedList().isPresent()) {
            this.blockedNumbers = syncMessage.getBlockedList().get().getNumbers();
        }
        if (syncMessage.getRead().isPresent()) {
            this.readMessages = syncMessage.getRead().get();
        }
    }
}
