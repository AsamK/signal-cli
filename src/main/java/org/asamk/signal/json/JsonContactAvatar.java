package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "ContactAvatar")
public record JsonContactAvatar(JsonAttachment attachment, boolean isProfile) {

    static JsonContactAvatar from(MessageEnvelope.Data.SharedContact.Avatar avatar) {
        return new JsonContactAvatar(JsonAttachment.from(avatar.attachment()), avatar.isProfile());
    }
}
