package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonContactPhone {

    @JsonProperty
    private final String value;

    @JsonProperty
    private final SharedContact.Phone.Type type;

    @JsonProperty
    private final String label;

    public JsonContactPhone(SharedContact.Phone phone) {
        value = phone.getValue();
        type = phone.getType();
        label = phone.getLabel().orNull();
    }
}
