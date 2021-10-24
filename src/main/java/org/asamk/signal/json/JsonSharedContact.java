package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

import java.util.List;
import java.util.stream.Collectors;

public record JsonSharedContact(
        JsonContactName name,
        JsonContactAvatar avatar,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactPhone> phone,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactEmail> email,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<JsonContactAddress> address,
        String organization
) {

    static JsonSharedContact from(SharedContact contact) {
        final var name = JsonContactName.from(contact.getName());
        final var avatar = contact.getAvatar().isPresent() ? JsonContactAvatar.from(contact.getAvatar().get()) : null;

        final var phone = contact.getPhone().isPresent() ? contact.getPhone()
                .get()
                .stream()
                .map(JsonContactPhone::from)
                .collect(Collectors.toList()) : null;

        final var email = contact.getEmail().isPresent() ? contact.getEmail()
                .get()
                .stream()
                .map(JsonContactEmail::from)
                .collect(Collectors.toList()) : null;

        final var address = contact.getAddress().isPresent() ? contact.getAddress()
                .get()
                .stream()
                .map(JsonContactAddress::from)
                .collect(Collectors.toList()) : null;

        final var organization = contact.getOrganization().orNull();

        return new JsonSharedContact(name, avatar, phone, email, address, organization);
    }
}
