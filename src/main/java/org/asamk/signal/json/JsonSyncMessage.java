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
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> blockedNumbers,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> blockedGroupIds,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonSyncReadMessage> readMessages,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncMessageType type
) {

    JsonSyncMessage(
            final JsonSyncDataMessage sentMessage,
            final List<String> blockedNumbers,
            final List<String> blockedGroupIds,
            final List<JsonSyncReadMessage> readMessages,
            final JsonSyncMessageType type
    ) {
        this.sentMessage = sentMessage;
        this.blockedNumbers = blockedNumbers;
        this.blockedGroupIds = blockedGroupIds;
        this.readMessages = readMessages;
        this.type = type;
    }

    static JsonSyncMessage from(MessageEnvelope.Sync syncMessage) {
        final var sentMessage = syncMessage.sent().isPresent()
                ? JsonSyncDataMessage.from(syncMessage.sent().get())
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
        return new JsonSyncMessage(sentMessage, blockedNumbers, blockedGroupIds, readMessages, type);
    }
}
