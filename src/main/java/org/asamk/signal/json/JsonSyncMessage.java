package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;

import java.util.List;

enum JsonSyncMessageType {
    CONTACTS_SYNC,
    GROUPS_SYNC,
    REQUEST_SYNC
}

record JsonSyncMessage(
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncDataMessage sentMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncStoryMessage sentStoryMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> blockedNumbers,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> blockedGroupIds,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonSyncReadMessage> readMessages,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncMessageType type
) {

    static JsonSyncMessage from(MessageEnvelope.Sync syncMessage) {
        final var sentMessage = syncMessage.sent().isPresent() && syncMessage.sent().get().story().isEmpty()
                ? JsonSyncDataMessage.from(syncMessage.sent().get())
                : null;
        final var sentStoryMessage = syncMessage.sent().isPresent() && syncMessage.sent().get().story().isPresent()
                ? JsonSyncStoryMessage.from(syncMessage.sent().get())
                : null;
        final List<String> blockedNumbers;
        final List<String> blockedGroupIds;
        if (syncMessage.blocked().isPresent()) {
            blockedNumbers = syncMessage.blocked()
                    .get()
                    .recipients()
                    .stream()
                    .map(RecipientAddress::getLegacyIdentifier)
                    .toList();
            blockedGroupIds = syncMessage.blocked().get().groupIds().stream().map(GroupId::toBase64).toList();
        } else {
            blockedNumbers = null;
            blockedGroupIds = null;
        }

        final var readMessages = syncMessage.read().size() > 0 ? syncMessage.read()
                .stream()
                .map(JsonSyncReadMessage::from)
                .toList() : null;

        final JsonSyncMessageType type;
        if (syncMessage.contacts().isPresent()) {
            type = JsonSyncMessageType.CONTACTS_SYNC;
        } else if (syncMessage.groups().isPresent()) {
            type = JsonSyncMessageType.GROUPS_SYNC;
        } else {
            type = null;
        }
        return new JsonSyncMessage(sentMessage, sentStoryMessage, blockedNumbers, blockedGroupIds, readMessages, type);
    }
}
