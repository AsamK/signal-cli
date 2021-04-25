package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonError {

    @JsonProperty
    final String message;

    public JsonError(Throwable exception) {
        this.message = exception.getMessage();
    }
}
