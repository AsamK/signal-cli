package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonMention {

    @JsonProperty
    @Deprecated
    final String name;

    @JsonProperty
    final String number;

    @JsonProperty
    final String uuid;

    @JsonProperty
    final int start;

    @JsonProperty
    final int length;

    JsonMention(SignalServiceDataMessage.Mention mention, Manager m) {
        final var address = m.resolveSignalServiceAddress(new SignalServiceAddress(mention.getUuid()));
        this.name = getLegacyIdentifier(address);
        this.number = address.getNumber().orNull();
        this.uuid = address.getUuid().toString();
        this.start = mention.getStart();
        this.length = mention.getLength();
    }
}
