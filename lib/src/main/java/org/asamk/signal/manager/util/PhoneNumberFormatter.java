package org.asamk.signal.manager.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.asamk.signal.manager.api.InvalidNumberException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhoneNumberFormatter {

    private static final Logger logger = LoggerFactory.getLogger(PhoneNumberFormatter.class);

    private static String impreciseFormatNumber(String number, String localNumber) {
        number = number.replaceAll("[^0-9+]", "");

        if (number.charAt(0) == '+') return number;

        if (localNumber.charAt(0) == '+') localNumber = localNumber.substring(1);

        if (localNumber.length() == number.length() || number.length() > localNumber.length()) return "+" + number;

        int difference = localNumber.length() - number.length();

        return "+" + localNumber.substring(0, difference) + number;
    }

    public static String formatNumber(String number, String localNumber) throws InvalidNumberException {
        if (number == null) {
            throw new InvalidNumberException("Null String passed as number.");
        }

        if (number.contains("@")) {
            throw new InvalidNumberException("Possible attempt to use email address.");
        }

        number = number.replaceAll("[^0-9+]", "");

        if (number.isEmpty()) {
            throw new InvalidNumberException("No valid characters found.");
        }

        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            PhoneNumber localNumberObject = util.parse(localNumber, null);

            String localCountryCode = util.getRegionCodeForNumber(localNumberObject);
            logger.trace("Got local CC: {}", localCountryCode);

            PhoneNumber numberObject = util.parse(number, localCountryCode);
            return util.format(numberObject, PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            logger.debug("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            return impreciseFormatNumber(number, localNumber);
        }
    }
}
