package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.InvalidNumberException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberFormatterTest {

    @Test
    void numberlessAccountsCanAddressInternationalPhoneNumbers() throws Exception {
        assertEquals("+12025550123", PhoneNumberFormatter.formatNumber("+1 (202) 555-0123", null));
    }

    @Test
    void numberlessAccountsCannotInferACountryCode() {
        assertThrows(InvalidNumberException.class, () -> PhoneNumberFormatter.formatNumber("2025550123", null));
    }

    @Test
    void numberedAccountsStillInferTheirCountryCode() throws Exception {
        assertEquals("+12025550123", PhoneNumberFormatter.formatNumber("2025550123", "+12025550124"));
    }
}
