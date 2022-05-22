package org.asamk.signal.manager;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.PinLockedException;

import java.io.Closeable;
import java.io.IOException;

public interface RegistrationManager extends Closeable {

    void register(
            boolean voiceVerification, String captcha
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException;

    void verifyAccount(
            String verificationCode, String pin
    ) throws IOException, PinLockedException, IncorrectPinException;

    void deleteLocalAccountData() throws IOException;

    boolean isRegistered();
}
