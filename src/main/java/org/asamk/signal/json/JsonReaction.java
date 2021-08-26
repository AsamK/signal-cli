package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Reaction;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonReaction {

    @JsonProperty
    final String emoji;

    @JsonProperty
    @Deprecated
    final String targetAuthor;

    @JsonProperty
    final String targetAuthorNumber;

    @JsonProperty
    final String targetAuthorUuid;

    @JsonProperty
    final long targetSentTimestamp;

    @JsonProperty
    final boolean isRemove;

    JsonReaction(Reaction reaction, Manager m) {
        this.emoji = reaction.getEmoji();
        final var address = m.resolveSignalServiceAddress(reaction.getTargetAuthor());
        this.targetAuthor = getLegacyIdentifier(address);
        this.targetAuthorNumber = address.getNumber().orNull();
        this.targetAuthorUuid = address.getUuid().toString();
        this.targetSentTimestamp = reaction.getTargetSentTimestamp();
        this.isRemove = reaction.isRemove();
    }
}
