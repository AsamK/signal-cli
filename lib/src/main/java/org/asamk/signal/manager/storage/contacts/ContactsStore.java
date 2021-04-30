package org.asamk.signal.manager.storage.contacts;

import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.whispersystems.libsignal.util.Pair;

import java.util.List;

public interface ContactsStore {

    void storeContact(RecipientId recipientId, Contact contact);

    Contact getContact(RecipientId recipientId);

    List<Pair<RecipientId, Contact>> getContacts();
}
