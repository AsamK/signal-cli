/*
  Copyright (C) 2015-2021 AsamK and contributors

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
package org.asamk.signal.manager;

import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.helper.PinHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class RegistrationManager implements Closeable {

    private SignalAccount account;
    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;

    private final SignalServiceAccountManager accountManager;
    private final PinHelper pinHelper;

    public RegistrationManager(
            SignalAccount account,
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent
    ) {
        this.account = account;
        this.pathConfig = pathConfig;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;

        final SleepTimer timer = new UptimeSleepTimer();
        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration()));
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        this.accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                new DynamicCredentialsProvider(
                        // Using empty UUID, because registering doesn't work otherwise
                        null, account.getUsername(), account.getPassword(), SignalServiceAddress.DEFAULT_DEVICE_ID),
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                timer);
        final var keyBackupService = accountManager.getKeyBackupService(ServiceConfig.getIasKeyStore(),
                serviceEnvironmentConfig.getKeyBackupConfig().getEnclaveName(),
                serviceEnvironmentConfig.getKeyBackupConfig().getServiceId(),
                serviceEnvironmentConfig.getKeyBackupConfig().getMrenclave(),
                10);
        this.pinHelper = new PinHelper(keyBackupService);
    }

    public static RegistrationManager init(
            String username, File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) throws IOException {
        var pathConfig = PathConfig.createDefault(settingsPath);

        final var serviceConfiguration = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);
        if (!SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            var identityKey = KeyUtils.generateIdentityKeyPair();
            var registrationId = KeyHelper.generateRegistrationId(false);

            var profileKey = KeyUtils.createProfileKey();
            var account = SignalAccount.create(pathConfig.getDataPath(),
                    username,
                    identityKey,
                    registrationId,
                    profileKey);
            account.save();

            return new RegistrationManager(account, pathConfig, serviceConfiguration, userAgent);
        }

        var account = SignalAccount.load(pathConfig.getDataPath(), username);

        return new RegistrationManager(account, pathConfig, serviceConfiguration, userAgent);
    }

    public void register(boolean voiceVerification, String captcha) throws IOException {
        if (account.getPassword() == null) {
            account.setPassword(KeyUtils.createPassword());
        }

        if (voiceVerification) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(),
                    Optional.fromNullable(captcha),
                    Optional.absent());
        } else {
            accountManager.requestSmsVerificationCode(false, Optional.fromNullable(captcha), Optional.absent());
        }

        account.save();
    }

    public Manager verifyAccount(
            String verificationCode, String pin
    ) throws IOException, KeyBackupSystemNoDataException, KeyBackupServicePinException {
        verificationCode = verificationCode.replace("-", "");
        VerifyAccountResponse response;
        try {
            response = verifyAccountWithCode(verificationCode, pin, null);
            account.setPinMasterKey(null);
        } catch (LockedException e) {
            if (pin == null) {
                throw e;
            }

            var registrationLockData = pinHelper.getRegistrationLockData(pin, e);
            if (registrationLockData == null) {
                throw e;
            }

            var registrationLock = registrationLockData.getMasterKey().deriveRegistrationLock();
            try {
                response = verifyAccountWithCode(verificationCode, null, registrationLock);
            } catch (LockedException _e) {
                throw new AssertionError("KBS Pin appeared to matched but reg lock still failed!");
            }
            account.setPinMasterKey(registrationLockData.getMasterKey());
        }

        // TODO response.isStorageCapable()
        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));

        account.setDeviceId(SignalServiceAddress.DEFAULT_DEVICE_ID);
        account.setMultiDevice(false);
        account.setRegistered(true);
        account.setUuid(UuidUtil.parseOrNull(response.getUuid()));
        account.setRegistrationLockPin(pin);
        account.getSessionStore().archiveAllSessions();
        final var recipientId = account.getRecipientStore().resolveRecipient(account.getSelfAddress());
        final var publicKey = account.getIdentityKeyPair().getPublicKey();
        account.getIdentityKeyStore().saveIdentity(recipientId, publicKey, new Date());
        account.getIdentityKeyStore().setIdentityTrustLevel(recipientId, publicKey, TrustLevel.TRUSTED_VERIFIED);

        Manager m = null;
        try {
            m = new Manager(account, pathConfig, serviceEnvironmentConfig, userAgent);

            m.refreshPreKeys();

            account.save();

            final var result = m;
            account = null;
            m = null;

            return result;
        } finally {
            if (m != null) {
                m.close();
            }
        }
    }

    private VerifyAccountResponse verifyAccountWithCode(
            final String verificationCode, final String legacyPin, final String registrationLock
    ) throws IOException {
        return accountManager.verifyAccountWithCode(verificationCode,
                null,
                account.getLocalRegistrationId(),
                true,
                legacyPin,
                registrationLock,
                account.getSelfUnidentifiedAccessKey(),
                account.isUnrestrictedUnidentifiedAccess(),
                ServiceConfig.capabilities,
                account.isDiscoverableByPhoneNumber());
    }

    @Override
    public void close() throws IOException {
        if (account != null) {
            account.close();
            account = null;
        }
    }
}
