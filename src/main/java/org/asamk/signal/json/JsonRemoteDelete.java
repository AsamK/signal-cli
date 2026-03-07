package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "RemoteDelete")
record JsonRemoteDelete(long timestamp) {}
