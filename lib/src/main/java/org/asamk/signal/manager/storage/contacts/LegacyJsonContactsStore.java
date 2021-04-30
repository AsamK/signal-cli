package org.asamk.signal.manager.storage.contacts;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class LegacyJsonContactsStore {

    @JsonProperty("contacts")
    private final List<LegacyContactInfo> contacts = new ArrayList<>();

    private LegacyJsonContactsStore() {
    }

    public List<LegacyContactInfo> getContacts() {
        return contacts;
    }
}
