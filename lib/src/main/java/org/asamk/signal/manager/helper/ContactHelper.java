package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.Contact;
import org.asamk.signal.manager.storage.SignalAccount;
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
        builder.withIsHidden(false);
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
        if (contact != null && contact.messageExpirationTime() == messageExpirationTimer) {
            return;
        }
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        final var version = contact == null
                ? 1
                : contact.messageExpirationTimeVersion() == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : contact.messageExpirationTimeVersion() + 1;
        account.getContactStore()
                .storeContact(recipientId,
                        builder.withMessageExpirationTime(messageExpirationTimer)
                                .withMessageExpirationTimeVersion(version)
                                .build());
    }

    public void setExpirationTimer(
            RecipientId recipientId,
            int messageExpirationTimer,
            int messageExpirationTimerVersion
    ) {
        var contact = account.getContactStore().getContact(recipientId);
        if (contact != null && (
                contact.messageExpirationTime() == messageExpirationTimer
                        || contact.messageExpirationTimeVersion() >= messageExpirationTimerVersion
        )) {
            return;
        }
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore()
                .storeContact(recipientId,
                        builder.withMessageExpirationTime(messageExpirationTimer)
                                .withMessageExpirationTimeVersion(messageExpirationTimerVersion)
                                .build());
    }

    public void setContactBlocked(RecipientId recipientId, boolean blocked) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        if (blocked) {
            builder.withIsProfileSharingEnabled(false);
        }
        account.getContactStore().storeContact(recipientId, builder.withIsBlocked(blocked).build());
    }

    public void setContactProfileSharing(RecipientId recipientId, boolean profileSharing) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        builder.withIsProfileSharingEnabled(profileSharing);
        account.getContactStore().storeContact(recipientId, builder.build());
    }

    public void setContactHidden(RecipientId recipientId, boolean hidden) {
        var contact = account.getContactStore().getContact(recipientId);
        final var builder = contact == null ? Contact.newBuilder() : Contact.newBuilder(contact);
        account.getContactStore().storeContact(recipientId, builder.withIsHidden(hidden).build());
    }
}
