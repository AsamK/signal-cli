package org.asamk.signal.manager.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipientIdentifierTest {

    private static final String LOCAL_NUMBER = "+4915112345678";
    private static final UUID ACI = UUID.fromString("84edbab3-8d1f-48f1-8bae-debb7bde3c6e");

    @Test
    void parsesAciWithoutDashesAsUuid() throws Exception {
        // Previously the letters were removed, and the remaining digits were used as phone number +49843814818736
        assertEquals(new RecipientIdentifier.Uuid(ACI),
                RecipientIdentifier.Single.fromString("84edbab38d1f48f18baedebb7bde3c6e", LOCAL_NUMBER));
        assertEquals(new RecipientIdentifier.Uuid(ACI),
                RecipientIdentifier.Single.fromString("84EDBAB38D1F48F18BAEDEBB7BDE3C6E", LOCAL_NUMBER));
    }

    @Test
    void parsesAciWithDashesAsUuid() throws Exception {
        assertEquals(new RecipientIdentifier.Uuid(ACI),
                RecipientIdentifier.Single.fromString(ACI.toString(), LOCAL_NUMBER));
    }

    @Test
    void parsesPhoneNumbers() throws Exception {
        assertEquals(new RecipientIdentifier.Number("+4915187654321"),
                RecipientIdentifier.Single.fromString("+4915187654321", LOCAL_NUMBER));
    }
}
