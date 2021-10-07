package org.asamk.signal.manager;

import java.io.File;

public class UserAlreadyExists extends Exception {

    private final String number;
    private final File fileName;

    public UserAlreadyExists(String number, File fileName) {
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
