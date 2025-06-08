/*
  Copyright (C) 2015-2022 AsamK and contributors

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.PinLockMissingException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.api.VerificationMethodNotAvailableException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.NumberVerificationUtils;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;

import java.io.IOException;
import java.util.function.Consumer;

import static org.asamk.signal.manager.util.KeyUtils.generatePreKeysForType;
import static org.asamk.signal.manager.util.Utils.handleResponseException;

public class RegistrationManagerImpl implements RegistrationManager {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationManagerImpl.class);

    private SignalAccount account;
    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final Consumer<Manager> newManagerListener;

    private final SignalServiceAccountManager unauthenticatedAccountManager;
    private final PinHelper pinHelper;
    private final AccountFileUpdater accountFileUpdater;

    public RegistrationManagerImpl(
            SignalAccount account,
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent,
            Consumer<Manager> newManagerListener,
            AccountFileUpdater accountFileUpdater
    ) {
        this.account = account;
        this.pathConfig = pathConfig;
        this.accountFileUpdater = accountFileUpdater;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;
        this.newManagerListener = newManagerListener;

        this.unauthenticatedAccountManager = SignalServiceAccountManager.createWithStaticCredentials(
                serviceEnvironmentConfig.signalServiceConfiguration(),
                // Using empty UUID, because registering doesn't work otherwise
                null,
                null,
                account.getNumber(),
                SignalServiceAddress.DEFAULT_DEVICE_ID,
                account.getPassword(),
                userAgent,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                ServiceConfig.GROUP_MAX_SIZE);
        final var secureValueRecovery = serviceEnvironmentConfig.svr2Mrenclaves()
                .stream()
                .map(mr -> (SecureValueRecovery) this.unauthenticatedAccountManager.getSecureValueRecoveryV2(mr))
                .toList();
        this.pinHelper = new PinHelper(secureValueRecovery);
    }

    @Override
    public void register(
            boolean voiceVerification,
            String captcha,
            final boolean forceRegister
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException, RateLimitException, VerificationMethodNotAvailableException {
        if (account.isRegistered()
                && account.getServiceEnvironment() != null
                && account.getServiceEnvironment() != serviceEnvironmentConfig.type()) {
            throw new IOException("Account is registered in another environment: " + account.getServiceEnvironment());
        }

        try {
            if (!forceRegister) {
                if (account.isRegistered()) {
                    throw new IOException("Account is already registered");
                }

                if (account.getAci() != null && attemptReactivateAccount()) {
                    return;
                }
            }

            final var recoveryPassword = account.getRecoveryPassword();
            if (recoveryPassword != null && account.isPrimaryDevice() && attemptReregisterAccount(recoveryPassword)) {
                return;
            }

            final var registrationApi = unauthenticatedAccountManager.getRegistrationApi();
            logger.trace("Creating verification session");
            String sessionId = NumberVerificationUtils.handleVerificationSession(registrationApi,
                    account.getSessionId(account.getNumber()),
                    id -> account.setSessionId(account.getNumber(), id),
                    voiceVerification,
                    captcha);
            logger.trace("Requesting verification code");
            NumberVerificationUtils.requestVerificationCode(registrationApi, sessionId, voiceVerification);
            logger.debug("Successfully requested verification code");
            account.setRegistered(false);
        } catch (DeprecatedVersionException e) {
            logger.debug("Signal-Server returned deprecated version exception", e);
            throw e;
        }
    }

    @Override
    public void verifyAccount(
            String verificationCode,
            String pin
    ) throws IOException, PinLockedException, IncorrectPinException, PinLockMissingException {
        if (account.isRegistered()) {
            throw new IOException("Account is already registered");
        }

        if (account.getPniIdentityKeyPair() == null) {
            account.setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
        }

        final var aciPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.ACI));
        final var pniPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.PNI));
        final var result = NumberVerificationUtils.verifyNumber(account.getSessionId(account.getNumber()),
                verificationCode,
                pin,
                pinHelper,
                (sessionId1, verificationCode1, registrationLock) -> verifyAccountWithCode(sessionId1,
                        verificationCode1,
                        registrationLock,
                        aciPreKeys,
                        pniPreKeys));
        final var response = result.first();
        final var masterKey = result.second();
        if (masterKey == null) {
            pin = null;
        }

        finishAccountRegistration(response, pin, masterKey, aciPreKeys, pniPreKeys);
    }

    @Override
    public void deleteLocalAccountData() throws IOException {
        account.deleteAccountData();
        accountFileUpdater.removeAccount();
        account = null;
    }

    @Override
    public boolean isRegistered() {
        return account.isRegistered();
    }

    private boolean attemptReregisterAccount(final String recoveryPassword) {
        try {
            if (account.getPniIdentityKeyPair() == null) {
                account.setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
            }

            final var aciPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.ACI));
            final var pniPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.PNI));
            final var registrationApi = unauthenticatedAccountManager.getRegistrationApi();
            final var response = handleResponseException(registrationApi.registerAccount(null,
                    recoveryPassword,
                    account.getAccountAttributes(null),
                    aciPreKeys,
                    pniPreKeys,
                    null,
                    true));
            finishAccountRegistration(response,
                    account.getRegistrationLockPin(),
                    account.getPinBackedMasterKey(),
                    aciPreKeys,
                    pniPreKeys);
            logger.info("Reregistered existing account, verify is not necessary.");
            return true;
        } catch (IOException e) {
            logger.debug("Failed to reregister account with recovery password", e);
        }
        return false;
    }

    private boolean attemptReactivateAccount() {
        try {
            final var dependencies = new SignalDependencies(serviceEnvironmentConfig,
                    userAgent,
                    account.getCredentialsProvider(),
                    account.getSignalServiceDataStore(),
                    null,
                    new ReentrantSignalSessionLock());
            handleResponseException(dependencies.getAccountApi()
                    .setAccountAttributes(account.getAccountAttributes(null)));
            account.setRegistered(true);
            logger.info("Reactivated existing account, verify is not necessary.");
            if (newManagerListener != null) {
                final var m = new ManagerImpl(account,
                        pathConfig,
                        accountFileUpdater,
                        serviceEnvironmentConfig,
                        userAgent);
                account = null;
                newManagerListener.accept(m);
            }
            return true;
        } catch (IOException e) {
            logger.debug("Failed to reactivate account");
        }
        return false;
    }

    private VerifyAccountResponse verifyAccountWithCode(
            final String sessionId,
            final String verificationCode,
            final String registrationLock,
            final PreKeyCollection aciPreKeys,
            final PreKeyCollection pniPreKeys
    ) throws IOException {
        final var registrationApi = unauthenticatedAccountManager.getRegistrationApi();
        try {
            handleResponseException(registrationApi.verifyAccount(sessionId, verificationCode));
        } catch (AlreadyVerifiedException e) {
            // Already verified so can continue registering
        }
        return handleResponseException(registrationApi.registerAccount(sessionId,
                null,
                account.getAccountAttributes(registrationLock),
                aciPreKeys,
                pniPreKeys,
                null,
                true));
    }

    private void finishAccountRegistration(
            final VerifyAccountResponse response,
            final String pin,
            final MasterKey masterKey,
            final PreKeyCollection aciPreKeys,
            final PreKeyCollection pniPreKeys
    ) throws IOException {
        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        final var aci = ACI.parseOrThrow(response.getUuid());
        final var pni = PNI.parseOrThrow(response.getPni());
        account.finishRegistration(aci, pni, masterKey, pin, aciPreKeys, pniPreKeys);
        accountFileUpdater.updateAccountIdentifiers(account.getNumber(), aci);

        ManagerImpl m = null;
        try {
            m = new ManagerImpl(account, pathConfig, accountFileUpdater, serviceEnvironmentConfig, userAgent);
            account = null;

            m.refreshPreKeys();
            if (response.isStorageCapable()) {
                m.syncRemoteStorage();
            }
            // Set an initial empty profile so user can be added to groups
            try {
                m.updateProfile(UpdateProfile.newBuilder().build());
            } catch (NoClassDefFoundError e) {
                logger.warn("Failed to set default profile: {}", e.getMessage());
            }

            try {
                m.refreshCurrentUsername();
            } catch (IOException | BaseUsernameException e) {
                logger.warn("Failed to refresh current username", e);
            }

            if (newManagerListener != null) {
                newManagerListener.accept(m);
                m = null;
            }
        } finally {
            if (m != null) {
                m.close();
            }
        }
    }

    @Override
    public void close() {
        if (account != null) {
            account.close();
            account = null;
        }
    }
}
