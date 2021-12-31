package org.asamk.signal.manager;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.identities.TrustNewIdentity;
import org.asamk.signal.manager.util.KeyUtils;
import org.whispersystems.libsignal.util.KeyHelper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public interface RegistrationManager extends Closeable {

    static RegistrationManager init(
            String number, File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) throws IOException {
        return init(number, settingsPath, serviceEnvironment, userAgent, null);
    }

    static RegistrationManager init(
            String number,
            File settingsPath,
            ServiceEnvironment serviceEnvironment,
            String userAgent,
            Consumer<Manager> newManagerListener
    ) throws IOException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        final var serviceConfiguration = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);
        if (!SignalAccount.userExists(pathConfig.dataPath(), number)) {
            var identityKey = KeyUtils.generateIdentityKeyPair();
            var registrationId = KeyHelper.generateRegistrationId(false);

            var profileKey = KeyUtils.createProfileKey();
            var account = SignalAccount.create(pathConfig.dataPath(),
                    number,
                    identityKey,
                    registrationId,
                    profileKey,
                    TrustNewIdentity.ON_FIRST_USE);

            return new RegistrationManagerImpl(account,
                    pathConfig,
                    serviceConfiguration,
                    userAgent,
                    newManagerListener);
        }

        var account = SignalAccount.load(pathConfig.dataPath(), number, true, TrustNewIdentity.ON_FIRST_USE);

        return new RegistrationManagerImpl(account, pathConfig, serviceConfiguration, userAgent, newManagerListener);
    }

    void register(boolean voiceVerification, String captcha) throws IOException, CaptchaRequiredException;

    void verifyAccount(
            String verificationCode, String pin
    ) throws IOException, PinLockedException, IncorrectPinException;
}
