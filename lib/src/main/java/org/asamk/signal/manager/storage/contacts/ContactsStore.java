package org.asamk.signal.manager.storage.contacts;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.recipients.RecipientId;

import java.util.List;

public interface ContactsStore {

    void storeContact(RecipientId recipientId, Contact contact);

    Contact getContact(RecipientId recipientId);

    List<Pair<RecipientId, Contact>> getContacts();

    void deleteContact(RecipientId recipientId);
}
