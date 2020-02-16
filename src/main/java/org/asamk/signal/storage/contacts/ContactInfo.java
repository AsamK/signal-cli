package org.asamk.signal.storage.contacts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ContactInfo {

    @JsonProperty
    public String name;

    @JsonProperty
    public String number;

    @JsonProperty
    public String color;

    @JsonProperty
    public String profileKey;

    @JsonProperty(defaultValue = "false")
    public boolean blocked;

    @JsonProperty
    public Integer inboxPosition;

    @JsonProperty(defaultValue = "false")
    public boolean archived;

    @JsonIgnore
    public SignalServiceAddress getAddress() {
        return new SignalServiceAddress(null, number);
    }
}
