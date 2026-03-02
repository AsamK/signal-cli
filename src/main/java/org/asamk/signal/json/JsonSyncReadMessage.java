package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.UUID;

@Schema(name = "SyncReadMessage")
record JsonSyncReadMessage(
    @Schema(required = true) @Deprecated String sender,
    @Schema(required = true) String senderNumber,
    @Schema(required = true) String senderUuid,
    @Schema(required = true) long timestamp
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
