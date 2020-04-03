package org.asamk.signal.json;

public class JsonError {

    String message;

    public JsonError(Throwable exception) {
        this.message = exception.getMessage();
    }
}
