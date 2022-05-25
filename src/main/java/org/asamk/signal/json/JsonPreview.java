package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

public record JsonPreview(String url, String title, String description, JsonAttachment image) {

    static JsonPreview from(MessageEnvelope.Data.Preview preview) {
        return new JsonPreview(preview.url(),
                preview.title(),
                preview.description(),
                preview.image().map(JsonAttachment::from).orElse(null));
    }
}
