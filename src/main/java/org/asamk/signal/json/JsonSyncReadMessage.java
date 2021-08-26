package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

class JsonSyncReadMessage {

    @JsonProperty
    @Deprecated
    final String sender;

    @JsonProperty
    final String senderNumber;

    @JsonProperty
    final String senderUuid;

    @JsonProperty
    final long timestamp;

    public JsonSyncReadMessage(final ReadMessage readMessage) {
        final var sender = readMessage.getSender();
        this.sender = getLegacyIdentifier(sender);
        this.senderNumber = sender.getNumber().orNull();
        this.senderUuid = sender.getUuid().toString();
        this.timestamp = readMessage.getTimestamp();
    }
}
