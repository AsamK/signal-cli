package org.asamk.signal.manager.api;

import java.io.File;

public class UserAlreadyExistsException extends Exception {

    private final String number;
    private final File fileName;

    public UserAlreadyExistsException(String number, File fileName) {
        this.number = number;
        this.fileName = fileName;
    }

    public String getNumber() {
        return number;
    }

    public File getFileName() {
        return fileName;
    }
}
