package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.VerificationMethodNotAvailableException;
import org.asamk.signal.manager.helper.PinHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushChallengeRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.PushServiceSocket.VerificationCodeTransport;
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

public class NumberVerificationUtils {

    private static final Logger logger = LoggerFactory.getLogger(NumberVerificationUtils.class);

    public static String handleVerificationSession(
            RegistrationApi registrationApi,
            String sessionId,
            Consumer<String> sessionIdSaver,
            boolean voiceVerification,
            String captcha
    ) throws CaptchaRequiredException, IOException, RateLimitException, VerificationMethodNotAvailableException {
        RegistrationSessionMetadataResponse sessionResponse;
        try {
            sessionResponse = getValidSession(registrationApi, sessionId);
        } catch (PushChallengeRequiredException |
                 org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException e) {
            if (captcha != null) {
                sessionResponse = submitCaptcha(registrationApi, sessionId, captcha);
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
        if (nextAttempt == null) {
            throw new VerificationMethodNotAvailableException();
        } else if (nextAttempt > 0) {
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
                sessionResponse = submitCaptcha(registrationApi, sessionId, captcha);
            }
            if (!sessionResponse.getBody().getAllowedToRequestCode()) {
                throw new CaptchaRequiredException("Captcha Required");
            }
        }

        return sessionId;
    }

    public static void requestVerificationCode(
            RegistrationApi registrationApi,
            String sessionId,
            boolean voiceVerification
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException {
        final var locale = Utils.getDefaultLocale(Locale.US);
        final var response = registrationApi.requestSmsVerificationCode(sessionId,
                locale,
                false,
                voiceVerification ? VerificationCodeTransport.VOICE : VerificationCodeTransport.SMS);
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
            String sessionId,
            String verificationCode,
            String pin,
            PinHelper pinHelper,
            Verifier verifier
    ) throws IOException, PinLockedException, IncorrectPinException {
        verificationCode = verificationCode.replace("-", "");
        try {
            final var response = verifier.verify(sessionId, verificationCode, null);

            return new Pair<>(response, null);
        } catch (LockedException e) {
            if (pin == null) {
                throw new PinLockedException(e.getTimeRemaining());
            }

            final var registrationLockData = pinHelper.getRegistrationLockData(pin, e);
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
            final RegistrationApi registrationApi,
            final String sessionId
    ) throws IOException {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NoSuchSessionException();
        }
        return Utils.handleResponseException(registrationApi.getRegistrationSessionStatus(sessionId));
    }

    private static RegistrationSessionMetadataResponse requestValidSession(
            final RegistrationApi registrationApi
    ) throws IOException {
        return Utils.handleResponseException(registrationApi.createRegistrationSession(null, "", ""));
    }

    private static RegistrationSessionMetadataResponse getValidSession(
            final RegistrationApi registrationApi,
            final String sessionId
    ) throws IOException {
        try {
            return validateSession(registrationApi, sessionId);
        } catch (NoSuchSessionException e) {
            logger.debug("No registration session, creating new one.");
            return requestValidSession(registrationApi);
        }
    }

    private static RegistrationSessionMetadataResponse submitCaptcha(
            RegistrationApi registrationApi,
            String sessionId,
            String captcha
    ) throws IOException, CaptchaRequiredException {
        captcha = captcha == null ? null : captcha.replace("signalcaptcha://", "");
        try {
            return Utils.handleResponseException(registrationApi.submitCaptchaToken(sessionId, captcha));
        } catch (PushChallengeRequiredException |
                 org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException |
                 TokenNotAcceptedException _e) {
            throw new CaptchaRequiredException("Captcha not accepted");
        } catch (NonSuccessfulResponseCodeException e) {
            if (e.code == 400) {
                throw new CaptchaRequiredException("Captcha has invalid format");
            }
            throw e;
        }
    }

    public interface Verifier {

        VerifyAccountResponse verify(
                String sessionId,
                String verificationCode,
                String registrationLock
        ) throws IOException;
    }
}
