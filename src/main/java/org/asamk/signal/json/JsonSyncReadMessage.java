package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

class JsonSyncReadMessage {

    @JsonProperty
    final String sender;

    @JsonProperty
    final long timestamp;

    public JsonSyncReadMessage(final String sender, final long timestamp) {
        this.sender = sender;
        this.timestamp = timestamp;
    }
}
