package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.helper.PinHelper;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushChallengeRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

public class NumberVerificationUtils {

    public static String handleVerificationSession(
            SignalServiceAccountManager accountManager,
            String sessionId,
            Consumer<String> sessionIdSaver,
            boolean voiceVerification,
            String captcha
    ) throws CaptchaRequiredException, IOException, RateLimitException {
        RegistrationSessionMetadataResponse sessionResponse;
        try {
            sessionResponse = getValidSession(accountManager, sessionId);
        } catch (PushChallengeRequiredException |
                 org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException e) {
            if (captcha != null) {
                sessionResponse = submitCaptcha(accountManager, sessionId, captcha);
            } else {
                throw new CaptchaRequiredException("Captcha Required");
            }
        }

        sessionId = sessionResponse.getBody().getId();
        sessionIdSaver.accept(sessionId);

        if (sessionResponse.getBody().getVerified()) {
            return sessionId;
        }

        if (sessionResponse.getBody().getAllowedToRequestCode()) {
            return sessionId;
        }

        final var nextAttempt = voiceVerification
                ? sessionResponse.getBody().getNextCall()
                : sessionResponse.getBody().getNextSms();
        if (nextAttempt != null && nextAttempt > 0) {
            final var timestamp = sessionResponse.getHeaders().getTimestamp() + nextAttempt * 1000;
            throw new RateLimitException(timestamp);
        }

        final var nextVerificationAttempt = sessionResponse.getBody().getNextVerificationAttempt();
        if (nextVerificationAttempt != null && nextVerificationAttempt > 0) {
            final var timestamp = sessionResponse.getHeaders().getTimestamp() + nextVerificationAttempt * 1000;
            throw new CaptchaRequiredException(timestamp);
        }

        if (sessionResponse.getBody().getRequestedInformation().contains("captcha")) {
            if (captcha != null) {
                sessionResponse = submitCaptcha(accountManager, sessionId, captcha);
            }
            if (!sessionResponse.getBody().getAllowedToRequestCode()) {
                throw new CaptchaRequiredException("Captcha Required");
            }
        }

        return sessionId;
    }

    public static void requestVerificationCode(
            SignalServiceAccountManager accountManager, String sessionId, boolean voiceVerification
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException {
        final ServiceResponse<RegistrationSessionMetadataResponse> response;
        final var locale = Utils.getDefaultLocale(Locale.US);
        if (voiceVerification) {
            response = accountManager.requestVoiceVerificationCode(sessionId, locale, false);
        } else {
            response = accountManager.requestSmsVerificationCode(sessionId, locale, false);
        }
        try {
            Utils.handleResponseException(response);
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
            String sessionId, String verificationCode, String pin, PinHelper pinHelper, Verifier verifier
    ) throws IOException, PinLockedException, IncorrectPinException {
        verificationCode = verificationCode.replace("-", "");
        try {
            final var response = verifier.verify(sessionId, verificationCode, null);

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
                response = verifier.verify(sessionId, verificationCode, registrationLock);
            } catch (LockedException _e) {
                throw new AssertionError("KBS Pin appeared to matched but reg lock still failed!");
            }

            return new Pair<>(response, registrationLockData.getMasterKey());
        }
    }

    private static RegistrationSessionMetadataResponse validateSession(
            final SignalServiceAccountManager accountManager, final String sessionId
    ) throws IOException {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NoSuchSessionException();
        }
        return Utils.handleResponseException(accountManager.getRegistrationSession(sessionId));
    }

    private static RegistrationSessionMetadataResponse requestValidSession(
            final SignalServiceAccountManager accountManager
    ) throws NoSuchSessionException, IOException {
        return Utils.handleResponseException(accountManager.createRegistrationSession(null, "", ""));
    }

    private static RegistrationSessionMetadataResponse getValidSession(
            final SignalServiceAccountManager accountManager, final String sessionId
    ) throws IOException {
        try {
            return validateSession(accountManager, sessionId);
        } catch (NoSuchSessionException e) {
            return requestValidSession(accountManager);
        }
    }

    private static RegistrationSessionMetadataResponse submitCaptcha(
            SignalServiceAccountManager accountManager, String sessionId, String captcha
    ) throws IOException, CaptchaRequiredException {
        captcha = captcha == null ? null : captcha.replace("signalcaptcha://", "");
        try {
            return Utils.handleResponseException(accountManager.submitCaptchaToken(sessionId, captcha));
        } catch (PushChallengeRequiredException |
                 org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException |
                 TokenNotAcceptedException _e) {
            throw new CaptchaRequiredException("Captcha not accepted");
        } catch (NonSuccessfulResponseCodeException e) {
            if (e.getCode() == 400) {
                throw new CaptchaRequiredException("Captcha has invalid format");
            }
            throw e;
        }
    }

    public interface Verifier {

        VerifyAccountResponse verify(
                String sessionId, String verificationCode, String registrationLock
        ) throws IOException;
    }
}
