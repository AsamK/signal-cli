package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.util.Util;

public record JsonContactName(
        String display, String given, String family, String prefix, String suffix, String middle
) {

    static JsonContactName from(MessageEnvelope.Data.SharedContact.Name name) {
        return new JsonContactName(Util.getStringIfNotBlank(name.display()),
                Util.getStringIfNotBlank(name.given()),
                Util.getStringIfNotBlank(name.family()),
                Util.getStringIfNotBlank(name.prefix()),
                Util.getStringIfNotBlank(name.suffix()),
                Util.getStringIfNotBlank(name.middle()));
    }
}
