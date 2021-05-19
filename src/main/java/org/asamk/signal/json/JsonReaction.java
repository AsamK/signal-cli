package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Reaction;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public class JsonReaction {

    @JsonProperty
    final String emoji;

    @JsonProperty
    final String targetAuthor;

    @JsonProperty
    final long targetSentTimestamp;

    @JsonProperty
    final boolean isRemove;

    JsonReaction(Reaction reaction, Manager m) {
        this.emoji = reaction.getEmoji();
        this.targetAuthor = getLegacyIdentifier(m.resolveSignalServiceAddress(reaction.getTargetAuthor()));
        this.targetSentTimestamp = reaction.getTargetSentTimestamp();
        this.isRemove = reaction.isRemove();
    }
}
