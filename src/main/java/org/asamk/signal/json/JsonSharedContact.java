package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.MessageEnvelope;

import java.util.List;

public record JsonSharedContact(
        JsonContactName name,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonContactAvatar avatar,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactPhone> phone,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactEmail> email,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactAddress> address,
        String organization
) {

    static JsonSharedContact from(MessageEnvelope.Data.SharedContact contact) {
        final var name = JsonContactName.from(contact.name());
        final var avatar = contact.avatar().isPresent() ? JsonContactAvatar.from(contact.avatar().get()) : null;

        final var phone = contact.phone().size() > 0
                ? contact.phone().stream().map(JsonContactPhone::from).toList()
                : null;

        final var email = contact.email().size() > 0
                ? contact.email().stream().map(JsonContactEmail::from).toList()
                : null;

        final var address = contact.address().size() > 0 ? contact.address()
                .stream()
                .map(JsonContactAddress::from)
                .toList() : null;

        final var organization = contact.organization().orElse(null);

        return new JsonSharedContact(name, avatar, phone, email, address, organization);
    }
}
