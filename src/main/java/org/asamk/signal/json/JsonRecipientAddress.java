package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.RecipientAddress;

import java.util.UUID;

@JsonSchema(title = "RecipientAddress")
public record JsonRecipientAddress(String uuid, String number, String username) {

    public static JsonRecipientAddress from(RecipientAddress address) {
        return new JsonRecipientAddress(address.uuid().map(UUID::toString).orElse(null),
                address.number().orElse(null),
                address.username().orElse(null));
    }
}
