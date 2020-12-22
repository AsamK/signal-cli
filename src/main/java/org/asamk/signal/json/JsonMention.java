package org.asamk.signal.json;

import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonMention {

    UUID uuid;
    int start;
    int length;

    JsonMention(SignalServiceDataMessage.Mention mention) {
        this.uuid = mention.getUuid();
        this.start = mention.getStart();
        this.length = mention.getLength();
    }

}
