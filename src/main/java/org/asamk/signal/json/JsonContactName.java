package org.asamk.signal.json;

import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public record JsonContactName(
        String display, String given, String family, String prefix, String suffix, String middle
) {

    static JsonContactName from(SharedContact.Name name) {
        return new JsonContactName(Util.getStringIfNotBlank(name.getDisplay()),
                Util.getStringIfNotBlank(name.getGiven()),
                Util.getStringIfNotBlank(name.getFamily()),
                Util.getStringIfNotBlank(name.getPrefix()),
                Util.getStringIfNotBlank(name.getSuffix()),
                Util.getStringIfNotBlank(name.getMiddle()));
    }
}
