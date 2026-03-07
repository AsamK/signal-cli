package org.asamk.signal.json;

import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.TextStyle;

@JsonSchema(title = "TextStyle")
public record JsonTextStyle(String style, int start, int length) {

    static JsonTextStyle from(TextStyle textStyle) {
        return new JsonTextStyle(textStyle.style().name(), textStyle.start(), textStyle.length());
    }
}
