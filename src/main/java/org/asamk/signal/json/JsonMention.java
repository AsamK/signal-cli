package org.asamk.signal.json;

import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonMention {

    UUID uuid;    // If possible, it would be nice to resolve this into their phone-number/name. Same for plain-text output
    int start;
    int length;

    JsonMention(SignalServiceDataMessage.Mention mention){
        this.uuid = mention.getUuid();
        this.start = mention.getStart();
        this.length = mention.getLength();
    }

}
