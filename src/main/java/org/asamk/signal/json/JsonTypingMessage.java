package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;

import java.util.Base64;

record JsonTypingMessage(
        String action, long timestamp, @JsonInclude(JsonInclude.Include.NON_NULL) String groupId
) {

    JsonTypingMessage(final String action, final long timestamp, final String groupId) {
        this.action = action;
        this.timestamp = timestamp;
        this.groupId = groupId;
    }

    static JsonTypingMessage from(SignalServiceTypingMessage typingMessage) {
        final var action = typingMessage.getAction().name();
        final var timestamp = typingMessage.getTimestamp();
        final var encoder = Base64.getEncoder();
        final var groupId = typingMessage.getGroupId().transform(encoder::encodeToString).orNull();
        return new JsonTypingMessage(action, timestamp, groupId);
    }
}
