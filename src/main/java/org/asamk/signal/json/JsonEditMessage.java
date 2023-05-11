package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

record JsonEditMessage(long targetSentTimestamp, JsonDataMessage dataMessage) {

    static JsonEditMessage from(MessageEnvelope.Edit editMessage) {
        return new JsonEditMessage(editMessage.targetSentTimestamp(), JsonDataMessage.from(editMessage.dataMessage()));
    }
}
