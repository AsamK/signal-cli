package org.asamk.signal.json;

import org.asamk.signal.manager.api.RecipientAddress;

import java.util.UUID;

public record JsonRecipientAddress(String uuid, String number) {

    public static JsonRecipientAddress from(RecipientAddress address) {
        return new JsonRecipientAddress(address.uuid().map(UUID::toString).orElse(null), address.number().orElse(null));
    }
}
