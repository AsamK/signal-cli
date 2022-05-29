package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.Contact;
import org.asamk.signal.manager.storage.recipients.RecipientId;

public class ContactHelper {

    private final SignalAccount account;

    public ContactHelper(final SignalAccount account) {
        this.account = account;
    }

    public boolean isContactBlocked(final RecipientId recipientId) {
        var sourceContact = account.getContactStore().getContact(recipientId);
        return sourceContact != null && sourceContact.isBlocked();
    }

    public void setContactName(final RecipientId recipientId, final String givenName, final String familyName) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        if (givenName != null) {
            builder.withGivenName(givenName);
        }
        if (familyName != null) {
            builder.withFamilyName(familyName);
        }
        account.getContactStore().storeContact(recipientId, builder.build());
    }

    public void setExpirationTimer(RecipientId recipientId, int messageExpirationTimer) {
        var contact = account.getContactStore().getContact(recipientId);
        if (contact != null && contact.getMessageExpirationTime() == messageExpirationTimer) {
            return;
        }
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore()
                .storeContact(recipientId, builder.withMessageExpirationTime(messageExpirationTimer).build());
    }

    public void setContactBlocked(RecipientId recipientId, boolean blocked) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        if (blocked) {
            builder.withProfileSharingEnabled(false);
        }
        account.getContactStore().storeContact(recipientId, builder.withBlocked(blocked).build());
    }
}
