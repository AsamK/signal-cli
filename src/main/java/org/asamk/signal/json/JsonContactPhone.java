package org.asamk.signal.json;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public record JsonContactPhone(String value, SharedContact.Phone.Type type, String label) {

    static JsonContactPhone from(SharedContact.Phone phone) {
        return new JsonContactPhone(phone.getValue(), phone.getType(), Util.getStringIfNotBlank(phone.getLabel()));
    }
}
