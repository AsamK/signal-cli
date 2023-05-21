package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.MessageEnvelope;

record JsonTypingMessage(
        String action, long timestamp, @JsonInclude(JsonInclude.Include.NON_NULL) String groupId
) {

    static JsonTypingMessage from(MessageEnvelope.Typing typingMessage) {
        final var action = typingMessage.type().name();
        final var timestamp = typingMessage.timestamp();
        final var groupId = typingMessage.groupId().map(GroupId::toBase64).orElse(null);
        return new JsonTypingMessage(action, timestamp, groupId);
    }
}
