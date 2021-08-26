package org.asamk.signal.manager.storage.contacts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.storage.recipients.RecipientAddress;

import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

public class LegacyContactInfo {

    @JsonProperty
    public String name;

    @JsonProperty
    public String number;

    @JsonProperty
    public UUID uuid;

    @JsonProperty
    public String color;

    @JsonProperty(defaultValue = "0")
    public int messageExpirationTime;

    @JsonProperty(access = WRITE_ONLY)
    public String profileKey;

    @JsonProperty(defaultValue = "false")
    public boolean blocked;

    @JsonProperty
    public Integer inboxPosition;

    @JsonProperty(defaultValue = "false")
    public boolean archived;

    public LegacyContactInfo() {
    }

    @JsonIgnore
    public RecipientAddress getAddress() {
        return new RecipientAddress(uuid, number);
    }
}
