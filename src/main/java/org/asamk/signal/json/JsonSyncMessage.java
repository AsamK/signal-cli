package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

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

    static JsonSyncMessage from(SignalServiceSyncMessage syncMessage, Manager m) {
        final var sentMessage = syncMessage.getSent().isPresent() ? JsonSyncDataMessage.from(syncMessage.getSent()
                .get(), m) : null;
        final List<String> blockedNumbers;
        final List<String> blockedGroupIds;
        if (syncMessage.getBlockedList().isPresent()) {
            final var base64 = Base64.getEncoder();
            blockedNumbers = syncMessage.getBlockedList()
                    .get()
                    .getAddresses()
                    .stream()
                    .map(Util::getLegacyIdentifier)
                    .collect(Collectors.toList());
            blockedGroupIds = syncMessage.getBlockedList()
                    .get()
                    .getGroupIds()
                    .stream()
                    .map(base64::encodeToString)
                    .collect(Collectors.toList());
        } else {
            blockedNumbers = null;
            blockedGroupIds = null;
        }

        final var readMessages = syncMessage.getRead().isPresent() ? syncMessage.getRead()
                .get()
                .stream()
                .map(JsonSyncReadMessage::from)
                .collect(Collectors.toList()) : null;

        final JsonSyncMessageType type;
        if (syncMessage.getContacts().isPresent()) {
            type = JsonSyncMessageType.CONTACTS_SYNC;
        } else if (syncMessage.getGroups().isPresent()) {
            type = JsonSyncMessageType.GROUPS_SYNC;
        } else if (syncMessage.getRequest().isPresent()) {
            type = JsonSyncMessageType.REQUEST_SYNC;
        } else {
            type = null;
        }
        return new JsonSyncMessage(sentMessage, blockedNumbers, blockedGroupIds, readMessages, type);
    }

    static JsonSyncMessage from(Signal.SyncMessageReceived messageReceived) {
        return new JsonSyncMessage(JsonSyncDataMessage.from(messageReceived), null, null, null, null);
    }
}
