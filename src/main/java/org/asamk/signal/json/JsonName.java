package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonName {

    @JsonProperty
    private final String display;

    @JsonProperty
    private final String given;

    @JsonProperty
    private final String family;

    @JsonProperty
    private final String prefix;

    @JsonProperty
    private final String suffix;

    @JsonProperty
    private final String middle;

    public JsonName(SharedContact.Name name) {
        display = name.getDisplay().orNull();
        given = name.getGiven().orNull();
        family = name.getFamily().orNull();
        prefix = name.getPrefix().orNull();
        suffix = name.getSuffix().orNull();
        middle = name.getMiddle().orNull();
    }
}
