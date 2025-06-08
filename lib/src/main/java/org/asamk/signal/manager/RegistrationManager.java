package org.asamk.signal.manager;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.PinLockMissingException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.VerificationMethodNotAvailableException;

import java.io.Closeable;
import java.io.IOException;

public interface RegistrationManager extends Closeable {

    void register(
            boolean voiceVerification,
            String captcha,
            final boolean forceRegister
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException, RateLimitException, VerificationMethodNotAvailableException;

    void verifyAccount(
            String verificationCode,
            String pin
    ) throws IOException, PinLockedException, IncorrectPinException, PinLockMissingException;

    void deleteLocalAccountData() throws IOException;

    boolean isRegistered();
}
