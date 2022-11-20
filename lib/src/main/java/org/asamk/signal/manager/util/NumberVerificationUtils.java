package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.helper.PinHelper;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

public class NumberVerificationUtils {

    public static void requestVerificationCode(
            SignalServiceAccountManager accountManager, String captcha, boolean voiceVerification
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException {
        captcha = captcha == null ? null : captcha.replace("signalcaptcha://", "");
        final ServiceResponse<RequestVerificationCodeResponse> response;
        if (voiceVerification) {
            response = accountManager.requestVoiceVerificationCode(Utils.getDefaultLocale(Locale.US),
                    Optional.ofNullable(captcha),
                    Optional.empty(),
                    Optional.empty());
        } else {
            response = accountManager.requestSmsVerificationCode(false,
                    Optional.ofNullable(captcha),
                    Optional.empty(),
                    Optional.empty());
        }
        try {
            handleResponseException(response);
        } catch (org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException e) {
            throw new CaptchaRequiredException(e.getMessage(), e);
        } catch (org.whispersystems.signalservice.api.push.exceptions.NonNormalizedPhoneNumberException e) {
            throw new NonNormalizedPhoneNumberException("Phone number is not normalized ("
                    + e.getMessage()
                    + "). Expected normalized: "
                    + e.getNormalizedNumber(), e);
        }
    }

    public static Pair<VerifyAccountResponse, MasterKey> verifyNumber(
            String verificationCode, String pin, PinHelper pinHelper, Verifier verifier
    ) throws IOException, PinLockedException, IncorrectPinException {
        verificationCode = verificationCode.replace("-", "");
        try {
            final var response = verifyAccountWithCode(verificationCode, null, verifier);

            return new Pair<>(response, null);
        } catch (LockedException e) {
            if (pin == null) {
                throw new PinLockedException(e.getTimeRemaining());
            }

            KbsPinData registrationLockData;
            registrationLockData = pinHelper.getRegistrationLockData(pin, e);
            if (registrationLockData == null) {
                throw e;
            }

            var registrationLock = registrationLockData.getMasterKey().deriveRegistrationLock();
            VerifyAccountResponse response;
            try {
                response = verifyAccountWithCode(verificationCode, registrationLock, verifier);
            } catch (LockedException _e) {
                throw new AssertionError("KBS Pin appeared to matched but reg lock still failed!");
            }

            return new Pair<>(response, registrationLockData.getMasterKey());
        }
    }

    private static VerifyAccountResponse verifyAccountWithCode(
            final String verificationCode, final String registrationLock, final Verifier verifier
    ) throws IOException {
        final var response = verifier.verify(verificationCode, registrationLock);
        handleResponseException(response);
        return response.getResult().get();
    }

    private static void handleResponseException(final ServiceResponse<?> response) throws IOException {
        final var throwableOptional = response.getExecutionError().or(response::getApplicationError);
        if (throwableOptional.isPresent()) {
            if (throwableOptional.get() instanceof IOException) {
                throw (IOException) throwableOptional.get();
            } else {
                throw new IOException(throwableOptional.get());
            }
        }
    }

    public interface Verifier {

        ServiceResponse<VerifyAccountResponse> verify(
                String verificationCode, String registrationLock
        );
    }
}
