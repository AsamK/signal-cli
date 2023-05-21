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
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.NumberVerificationUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

import java.io.IOException;
import java.util.function.Consumer;

public class RegistrationManagerImpl implements RegistrationManager {

    private final static Logger logger = LoggerFactory.getLogger(RegistrationManagerImpl.class);

    private SignalAccount account;
    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final Consumer<Manager> newManagerListener;

    private final SignalServiceAccountManager accountManager;
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

        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration()),
                    ServiceConfig.GROUP_MAX_SIZE);
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        this.accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                new DynamicCredentialsProvider(
                        // Using empty UUID, because registering doesn't work otherwise
                        null, null, account.getNumber(), account.getPassword(), SignalServiceAddress.DEFAULT_DEVICE_ID),
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);
        final var keyBackupService = accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
                serviceEnvironmentConfig.getKeyBackupConfig().getEnclaveName(),
                serviceEnvironmentConfig.getKeyBackupConfig().getServiceId(),
                serviceEnvironmentConfig.getKeyBackupConfig().getMrenclave(),
                10);
        final var fallbackKeyBackupServices = serviceEnvironmentConfig.getFallbackKeyBackupConfigs()
                .stream()
                .map(config -> accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
                        config.getEnclaveName(),
                        config.getServiceId(),
                        config.getMrenclave(),
                        10))
                .toList();
        this.pinHelper = new PinHelper(keyBackupService, fallbackKeyBackupServices);
    }

    @Override
    public void register(
            boolean voiceVerification, String captcha
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException, RateLimitException {
        if (account.isRegistered()
                && account.getServiceEnvironment() != null
                && account.getServiceEnvironment() != serviceEnvironmentConfig.getType()) {
            throw new IOException("Account is registered in another environment: " + account.getServiceEnvironment());
        }

        try {
            if (account.getAci() != null && attemptReactivateAccount()) {
                return;
            }

            String sessionId = NumberVerificationUtils.handleVerificationSession(accountManager,
                    account.getSessionId(account.getNumber()),
                    id -> account.setSessionId(account.getNumber(), id),
                    voiceVerification,
                    captcha);
            NumberVerificationUtils.requestVerificationCode(accountManager, sessionId, voiceVerification);
        } catch (DeprecatedVersionException e) {
            logger.debug("Signal-Server returned deprecated version exception", e);
            throw e;
        }
    }

    @Override
    public void verifyAccount(
            String verificationCode, String pin
    ) throws IOException, PinLockedException, IncorrectPinException {
        var sessionId = account.getSessionId(account.getNumber());
        final var result = NumberVerificationUtils.verifyNumber(sessionId,
                verificationCode,
                pin,
                pinHelper,
                this::verifyAccountWithCode);
        final var response = result.first();
        final var masterKey = result.second();
        if (masterKey == null) {
            pin = null;
        }

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        final var aci = ACI.parseOrNull(response.getUuid());
        final var pni = PNI.parseOrNull(response.getPni());
        account.finishRegistration(aci, pni, masterKey, pin);
        accountFileUpdater.updateAccountIdentifiers(account.getNumber(), aci);

        ManagerImpl m = null;
        try {
            m = new ManagerImpl(account, pathConfig, accountFileUpdater, serviceEnvironmentConfig, userAgent);
            account = null;

            m.refreshPreKeys();
            if (response.isStorageCapable()) {
                m.retrieveRemoteStorage();
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
    public void deleteLocalAccountData() throws IOException {
        account.deleteAccountData();
        accountFileUpdater.removeAccount();
        account = null;
    }

    @Override
    public boolean isRegistered() {
        return account.isRegistered();
    }

    private boolean attemptReactivateAccount() {
        try {
            final var accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                    account.getCredentialsProvider(),
                    userAgent,
                    null,
                    ServiceConfig.AUTOMATIC_NETWORK_RETRY);
            accountManager.setAccountAttributes(account.getAccountAttributes(null));
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
            final String sessionId, final String verificationCode, final String registrationLock
    ) throws IOException {
        try {
            Utils.handleResponseException(accountManager.verifyAccount(verificationCode, sessionId));
        } catch (AlreadyVerifiedException e) {
            // Already verified so can continue registering
        }
        return Utils.handleResponseException(accountManager.registerAccount(sessionId,
                null,
                account.getAccountAttributes(registrationLock),
                true));
    }

    @Override
    public void close() {
        if (account != null) {
            account.close();
            account = null;
        }
    }
}
