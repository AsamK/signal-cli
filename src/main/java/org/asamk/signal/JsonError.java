package org.asamk.signal;

class JsonError {
    String message;

    JsonError(Throwable exception) {
        this.message = exception.getMessage();
    }
}
