package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.jsonschema.JsonSchema;

import org.asamk.signal.manager.api.InvalidEnvelopeContentException;

import java.util.List;

@JsonSchema(title = "Error")
public record JsonError(
        String message,
        String type,
        @JsonInclude(JsonInclude.Include.NON_NULL) Details details
) {

    public static JsonError from(Throwable exception) {
        final var details = exception instanceof InvalidEnvelopeContentException e
                ? new Details(e.getCode(), e.getBodyLength(), e.getInvalidBodyRanges())
                : null;
        return new JsonError(exception.getMessage(), exception.getClass().getSimpleName(), details);
    }

    public record Details(
            String code,
            Integer bodyLength,
            List<InvalidEnvelopeContentException.InvalidBodyRange> invalidBodyRanges
    ) {}
}
