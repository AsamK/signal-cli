package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonContactEmail {

    @JsonProperty
    private final String value;

    @JsonProperty
    private final SharedContact.Email.Type type;

    @JsonProperty
    private final String label;

    public JsonContactEmail(SharedContact.Email email) {
        value = email.getValue();
        type = email.getType();
        label = email.getLabel().orNull();
    }
}
