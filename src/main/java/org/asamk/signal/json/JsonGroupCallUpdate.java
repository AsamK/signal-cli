package org.asamk.signal.json;

import org.asamk.signal.manager.api.MessageEnvelope;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "GroupCallUpdate")
record JsonGroupCallUpdate(String eraId) {

    static JsonGroupCallUpdate from(MessageEnvelope.Data.GroupCallUpdate groupCallUpdate) {
        return new JsonGroupCallUpdate(groupCallUpdate.eraId());
    }
}
