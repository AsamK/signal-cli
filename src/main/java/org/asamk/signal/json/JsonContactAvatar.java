package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.MessageEnvelope;

@Schema(name = "ContactAvatar")
public record JsonContactAvatar(
        @Schema(required = true) JsonAttachment attachment,
        @Schema(required = true) boolean isProfile
) {

    static JsonContactAvatar from(MessageEnvelope.Data.SharedContact.Avatar avatar) {
        return new JsonContactAvatar(JsonAttachment.from(avatar.attachment()), avatar.isProfile());
    }
}
