package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

public record JsonContactEmail(String value, String type, String label) {

    static JsonContactEmail from(MessageEnvelope.Data.SharedContact.Email email) {
        return new JsonContactEmail(email.value(), email.type().name(), Util.getStringIfNotBlank(email.label()));
    }
}
