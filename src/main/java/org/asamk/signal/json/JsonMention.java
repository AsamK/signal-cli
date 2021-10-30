package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public record JsonMention(@Deprecated String name, String number, String uuid, int start, int length) {

    static JsonMention from(SignalServiceDataMessage.Mention mention, Manager m) {
        final var address = m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid()));
        return new JsonMention(getLegacyIdentifier(address),
                address.getNumber().orNull(),
                address.getUuid().toString(),
                mention.getStart(),
                mention.getLength());
    }
}
