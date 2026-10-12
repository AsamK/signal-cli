package org.asamk.signal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountIdentifierTest {

    @Test
    void normalizesSignalAndroidAccountKeys() {
        assertEquals("a6b28482-2e32-83d0-7f23-91360a4c2b91",
                AccountIdentifier.normalize("A6B284822E3283D07F2391360A4C2B91"));
        assertEquals("a6b28482-2e32-83d0-7f23-91360a4c2b91",
                AccountIdentifier.normalize("A6B28482-2E32-83D0-7F23-91360A4C2B91"));
    }

    @Test
    void leavesNonAccountIdentifiersUnchanged() {
        assertEquals("+12025550123", AccountIdentifier.normalize("+12025550123"));
        assertEquals("not-an-account", AccountIdentifier.normalize("not-an-account"));
        assertEquals("NOT-AN-ACCOUNT", AccountIdentifier.normalize("NOT-AN-ACCOUNT"));
    }
}
