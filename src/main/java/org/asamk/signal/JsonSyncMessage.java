package org.asamk.signal;

import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

import java.util.List;

enum JsonSyncMessageType {
  CONTACTS_SYNC,
  GROUPS_SYNC,
  REQUEST_SYNC
}

class JsonSyncMessage {

    JsonSyncDataMessage sentMessage;
    List<String> blockedNumbers;
    List<ReadMessage> readMessages;
    JsonSyncMessageType type;

    JsonSyncMessage(SignalServiceSyncMessage syncMessage) {
       if (syncMessage.getSent().isPresent()) {
           this.sentMessage = new JsonSyncDataMessage(syncMessage.getSent().get());
       }
       if (syncMessage.getBlockedList().isPresent()) {
           this.blockedNumbers = syncMessage.getBlockedList().get().getNumbers();
       }
       if (syncMessage.getRead().isPresent()) {
           this.readMessages = syncMessage.getRead().get();
       }

       if (syncMessage.getContacts().isPresent()) {
           this.type = JsonSyncMessageType.CONTACTS_SYNC;
       } else if (syncMessage.getGroups().isPresent()) {
           this.type = JsonSyncMessageType.GROUPS_SYNC;
       } else if (syncMessage.getRequest().isPresent()) {
           this.type = JsonSyncMessageType.REQUEST_SYNC;
       }
   }
}
