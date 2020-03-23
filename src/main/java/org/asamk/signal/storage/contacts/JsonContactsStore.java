package org.asamk.signal.storage.contacts;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;

public class JsonContactsStore {

    @JsonProperty("contacts")
    private List<ContactInfo> contacts = new ArrayList<>();

    public void updateContact(ContactInfo contact) {
        final SignalServiceAddress contactAddress = contact.getAddress();
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getAddress().matches(contactAddress)) {
                contacts.set(i, contact);
                return;
            }
        }

        contacts.add(contact);
    }

    public ContactInfo getContact(SignalServiceAddress address) {
        for (ContactInfo contact : contacts) {
            if (contact.getAddress().matches(address)) {
                return contact;
            }
        }
        return null;
    }

    public List<ContactInfo> getContacts() {
        return new ArrayList<>(contacts);
    }

    /**
     * Remove all contacts from the store
     */
    public void clear() {
        contacts.clear();
    }
}
