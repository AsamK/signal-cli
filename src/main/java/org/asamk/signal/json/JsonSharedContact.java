package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

import java.util.List;
import java.util.stream.Collectors;

public class JsonSharedContact {

    @JsonProperty
    final JsonContactName name;

    @JsonProperty
    final JsonContactAvatar avatar;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonContactPhone> phone;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonContactEmail> email;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final List<JsonContactAddress> address;

    @JsonProperty
    final String organization;

    public JsonSharedContact(SharedContact contact) {
        name = new JsonContactName(contact.getName());
        if (contact.getAvatar().isPresent()) {
            avatar = new JsonContactAvatar(contact.getAvatar().get());
        } else {
            avatar = null;
        }

        if (contact.getPhone().isPresent()) {
            phone = contact.getPhone().get().stream().map(JsonContactPhone::new).collect(Collectors.toList());
        } else {
            phone = null;
        }

        if (contact.getEmail().isPresent()) {
            email = contact.getEmail().get().stream().map(JsonContactEmail::new).collect(Collectors.toList());
        } else {
            email = null;
        }

        if (contact.getAddress().isPresent()) {
            address = contact.getAddress().get().stream().map(JsonContactAddress::new).collect(Collectors.toList());
        } else {
            address = null;
        }

        organization = contact.getOrganization().orNull();
    }
}
