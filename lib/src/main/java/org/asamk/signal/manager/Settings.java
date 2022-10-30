package org.asamk.signal.manager;

import org.asamk.signal.manager.storage.identities.TrustNewIdentity;

public record Settings(TrustNewIdentity trustNewIdentity, boolean disableMessageSendLog) {

    public static Settings DEFAULT = new Settings(TrustNewIdentity.ON_FIRST_USE, false);
}
