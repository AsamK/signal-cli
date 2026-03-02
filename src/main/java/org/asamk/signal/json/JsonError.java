package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Error")
public record JsonError(@Schema(required = true) String message, @Schema(required = true) String type) {

    public static JsonError from(Throwable exception) {
        return new JsonError(exception.getMessage(), exception.getClass().getSimpleName());
    }
}
