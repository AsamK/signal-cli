package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonContactName {

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

    public JsonContactName(SharedContact.Name name) {
        display = Util.getStringIfNotBlank(name.getDisplay());
        given = Util.getStringIfNotBlank(name.getGiven());
        family = Util.getStringIfNotBlank(name.getFamily());
        prefix = Util.getStringIfNotBlank(name.getPrefix());
        suffix = Util.getStringIfNotBlank(name.getSuffix());
        middle = Util.getStringIfNotBlank(name.getMiddle());
    }
}
