package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;


@JsonSchema(title = "Error")
public record JsonError(String message, String type) {

    public static JsonError from(Throwable exception) {
        return new JsonError(exception.getMessage(), exception.getClass().getSimpleName());
    }
}
