package org.asamk.signal;

public class UserAlreadyExists extends Exception {
    private String username;
    private String fileName;

    public UserAlreadyExists(String username, String fileName) {
        this.username = username;
        this.fileName = fileName;
    }

    public String getUsername() {
        return username;
    }

    public String getFileName() {
        return fileName;
    }
}
