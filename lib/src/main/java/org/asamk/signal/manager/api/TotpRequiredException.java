package org.asamk.signal.manager.api;

import java.io.IOException;

public class TotpRequiredException extends IOException {

    public TotpRequiredException() {
        super("A TOTP token is required or the supplied token is incorrect");
    }
}
