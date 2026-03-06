package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "RemoteDelete")
record JsonRemoteDelete(@JsonProperty(required = true) long timestamp) {}
