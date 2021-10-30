package org.asamk.signal.json;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public record JsonContactEmail(String value, SharedContact.Email.Type type, String label) {

    static JsonContactEmail from(SharedContact.Email email) {
        return new JsonContactEmail(email.getValue(), email.getType(), Util.getStringIfNotBlank(email.getLabel()));
    }
}
