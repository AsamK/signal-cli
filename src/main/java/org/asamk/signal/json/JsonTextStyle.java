package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;
import org.asamk.signal.manager.api.TextStyle;

@Schema(name = "TextStyle")
public record JsonTextStyle(
        @Schema(required = true) String style,
        @Schema(required = true) int start,
        @Schema(required = true) int length
) {

    static JsonTextStyle from(TextStyle textStyle) {
        return new JsonTextStyle(textStyle.style().name(), textStyle.start(), textStyle.length());
    }
}
