package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Reaction;

public class JsonReaction {

    String emoji;
    String targetAuthor;
    long targetSentTimestamp;
    boolean isRemove;

    JsonReaction(Reaction reaction, final Manager m){
        this.emoji = reaction.getEmoji();
        this.targetAuthor = m.resolveSignalServiceAddress(reaction.getTargetAuthor()).getLegacyIdentifier();
        this.targetSentTimestamp = reaction.getTargetSentTimestamp();
        this.isRemove = reaction.isRemove();
    }
}
