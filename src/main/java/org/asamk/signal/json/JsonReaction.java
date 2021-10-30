package org.asamk.signal.json;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Reaction;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

public record JsonReaction(
        String emoji,
        @Deprecated String targetAuthor,
        String targetAuthorNumber,
        String targetAuthorUuid,
        long targetSentTimestamp,
        boolean isRemove
) {

    static JsonReaction from(Reaction reaction, Manager m) {
        final var emoji = reaction.getEmoji();
        final var address = m.resolveSignalServiceAddress(reaction.getTargetAuthor());
        final var targetAuthor = getLegacyIdentifier(address);
        final var targetAuthorNumber = address.getNumber().orNull();
        final var targetAuthorUuid = address.getUuid().toString();
        final var targetSentTimestamp = reaction.getTargetSentTimestamp();
        final var isRemove = reaction.isRemove();
        return new JsonReaction(emoji,
                targetAuthor,
                targetAuthorNumber,
                targetAuthorUuid,
                targetSentTimestamp,
                isRemove);
    }
}
