package org.asamk.signal.storage.contacts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ContactInfo {
    @JsonProperty
    public String name;

    @JsonProperty
    public String number;

    @JsonProperty
    public String color;
}
