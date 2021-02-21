package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;

import java.util.Base64;

class JsonTypingMessage {

    @JsonProperty
    final String action;

    @JsonProperty
    final long timestamp;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final String groupId;

    JsonTypingMessage(SignalServiceTypingMessage typingMessage) {
        this.action = typingMessage.getAction().name();
        this.timestamp = typingMessage.getTimestamp();
        final var encoder = Base64.getEncoder();
        this.groupId = typingMessage.getGroupId().transform(encoder::encodeToString).orNull();
    }
}
