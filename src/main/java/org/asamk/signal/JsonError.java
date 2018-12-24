package org.asamk.signal;

public class JsonError {

    String message;

    public JsonError(Throwable exception) {
        this.message = exception.getMessage();
    }
}
