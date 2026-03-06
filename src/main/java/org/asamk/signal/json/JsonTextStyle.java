package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.TextStyle;

@JsonSchema(title = "TextStyle")
public record JsonTextStyle(
        @JsonProperty(required = true) String style,
        @JsonProperty(required = true) int start,
        @JsonProperty(required = true) int length
) {

    static JsonTextStyle from(TextStyle textStyle) {
        return new JsonTextStyle(textStyle.style().name(), textStyle.start(), textStyle.length());
    }
}
