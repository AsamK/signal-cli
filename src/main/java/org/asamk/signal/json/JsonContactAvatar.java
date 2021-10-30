package org.asamk.signal.json;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public record JsonContactAvatar(JsonAttachment attachment, boolean isProfile) {

    static JsonContactAvatar from(SharedContact.Avatar avatar) {
        return new JsonContactAvatar(JsonAttachment.from(avatar.getAttachment()), avatar.isProfile());
    }
}
