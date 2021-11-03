package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

public record JsonContactPhone(String value, String type, String label) {

    static JsonContactPhone from(MessageEnvelope.Data.SharedContact.Phone phone) {
        return new JsonContactPhone(phone.value(), phone.type().name(), Util.getStringIfNotBlank(phone.label()));
    }
}
