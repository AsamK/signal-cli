package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

record JsonSyncReadMessage(
        @Deprecated String sender, String senderNumber, String senderUuid, long timestamp
) {

    static JsonSyncReadMessage from(final ReadMessage readMessage) {
        final var senderAddress = readMessage.getSender();
        final var sender = getLegacyIdentifier(senderAddress);
        final var senderNumber = senderAddress.getNumber().orNull();
        final var senderUuid = senderAddress.getUuid().toString();
        final var timestamp = readMessage.getTimestamp();
        return new JsonSyncReadMessage(sender, senderNumber, senderUuid, timestamp);
    }
}
