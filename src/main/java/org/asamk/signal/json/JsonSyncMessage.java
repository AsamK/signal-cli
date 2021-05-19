package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

enum JsonSyncMessageType {
    CONTACTS_SYNC,
    GROUPS_SYNC,
    REQUEST_SYNC
}

class JsonSyncMessage {

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonSyncDataMessage sentMessage;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<String> blockedNumbers;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<String> blockedGroupIds;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonSyncReadMessage> readMessages;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final JsonSyncMessageType type;

    JsonSyncMessage(SignalServiceSyncMessage syncMessage, Manager m) {
        this.sentMessage = syncMessage.getSent().isPresent()
                ? new JsonSyncDataMessage(syncMessage.getSent().get(), m)
                : null;
        if (syncMessage.getBlockedList().isPresent()) {
            final var base64 = Base64.getEncoder();
            this.blockedNumbers = syncMessage.getBlockedList()
                    .get()
                    .getAddresses()
                    .stream()
                    .map(Util::getLegacyIdentifier)
                    .collect(Collectors.toList());
            this.blockedGroupIds = syncMessage.getBlockedList()
                    .get()
                    .getGroupIds()
                    .stream()
                    .map(base64::encodeToString)
                    .collect(Collectors.toList());
        } else {
            this.blockedNumbers = null;
            this.blockedGroupIds = null;
        }
        if (syncMessage.getRead().isPresent()) {
            this.readMessages = syncMessage.getRead()
                    .get()
                    .stream()
                    .map(message -> new JsonSyncReadMessage(getLegacyIdentifier(message.getSender()),
                            message.getTimestamp()))
                    .collect(Collectors.toList());
        } else {
            this.readMessages = null;
        }

        if (syncMessage.getContacts().isPresent()) {
            this.type = JsonSyncMessageType.CONTACTS_SYNC;
        } else if (syncMessage.getGroups().isPresent()) {
            this.type = JsonSyncMessageType.GROUPS_SYNC;
        } else if (syncMessage.getRequest().isPresent()) {
            this.type = JsonSyncMessageType.REQUEST_SYNC;
        } else {
            this.type = null;
        }
    }

    JsonSyncMessage(Signal.SyncMessageReceived messageReceived) {
        this.sentMessage = new JsonSyncDataMessage(messageReceived);
        this.blockedNumbers = null;
        this.blockedGroupIds = null;
        this.readMessages = null;
        this.type = null;
    }
}
