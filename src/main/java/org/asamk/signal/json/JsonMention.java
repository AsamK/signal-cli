package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class JsonMention {

    String name;
    int start;
    int length;

    JsonMention(SignalServiceDataMessage.Mention mention, Manager m) {
        this.name = m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid(), null))
                .getLegacyIdentifier();
        this.start = mention.getStart();
        this.length = mention.getLength();
    }
}
