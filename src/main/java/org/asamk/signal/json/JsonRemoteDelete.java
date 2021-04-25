package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

class JsonRemoteDelete {

    @JsonProperty
    final long timestamp;

    JsonRemoteDelete(SignalServiceDataMessage.RemoteDelete remoteDelete) {
        this.timestamp = remoteDelete.getTargetSentTimestamp();
    }
}
