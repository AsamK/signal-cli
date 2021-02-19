package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class JsonContactAvatar {

    @JsonProperty
    private final JsonAttachment attachment;

    @JsonProperty
    private final boolean isProfile;

    public JsonContactAvatar(SharedContact.Avatar avatar) {
        attachment = new JsonAttachment(avatar.getAttachment());
        isProfile = avatar.isProfile();
    }
}
