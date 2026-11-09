package org.asamk.signal.manager;

import org.asamk.signal.manager.api.TrustNewIdentity;

public record Settings(TrustNewIdentity trustNewIdentity, boolean disableMessageSendLog, String tlsProxy) {

    public static final Settings DEFAULT = new Settings(TrustNewIdentity.ON_FIRST_USE, false, null);
}
