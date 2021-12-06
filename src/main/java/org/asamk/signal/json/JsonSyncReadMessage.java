package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

record JsonSyncReadMessage(
        @Deprecated String sender, String senderNumber, String senderUuid, long timestamp
) {

    static JsonSyncReadMessage from(MessageEnvelope.Sync.Read readMessage) {
        final var senderAddress = readMessage.sender();
        final var sender = senderAddress.getLegacyIdentifier();
        final var senderNumber = senderAddress.number().orElse(null);
        final var senderUuid = senderAddress.uuid().map(UUID::toString).orElse(null);
        final var timestamp = readMessage.timestamp();
        return new JsonSyncReadMessage(sender, senderNumber, senderUuid, timestamp);
    }
}
