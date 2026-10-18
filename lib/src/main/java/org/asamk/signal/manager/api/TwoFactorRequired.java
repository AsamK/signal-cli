package org.asamk.signal.manager.api;

import java.io.IOException;

public class TwoFactorRequired extends IOException {

    public TwoFactorRequired() {
        super("A TOTP token is required or the supplied token is incorrect");
    }
}
