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
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public class ProvisioningManager {

    private final static Logger logger = LoggerFactory.getLogger(ProvisioningManager.class);

    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;

    private final SignalServiceAccountManager accountManager;
    private final IdentityKeyPair tempIdentityKey;
    private final int registrationId;
    private final String password;

    ProvisioningManager(PathConfig pathConfig, ServiceEnvironmentConfig serviceEnvironmentConfig, String userAgent) {
        this.pathConfig = pathConfig;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;

        tempIdentityKey = KeyUtils.generateIdentityKeyPair();
        registrationId = KeyHelper.generateRegistrationId(false);
        password = KeyUtils.createPassword();
        final SleepTimer timer = new UptimeSleepTimer();
        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration()));
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                new DynamicCredentialsProvider(null, null, password, SignalServiceAddress.DEFAULT_DEVICE_ID),
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                timer);
    }

    public static ProvisioningManager init(
            File settingsPath, ServiceEnvironment serviceEnvironment, String userAgent
    ) {
        var pathConfig = PathConfig.createDefault(settingsPath);

        final var serviceConfiguration = ServiceConfig.getServiceEnvironmentConfig(serviceEnvironment, userAgent);

        return new ProvisioningManager(pathConfig, serviceConfiguration, userAgent);
    }

    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        var deviceUuid = accountManager.getNewDeviceUuid();

        return new DeviceLinkInfo(deviceUuid, tempIdentityKey.getPublicKey().getPublicKey()).createDeviceLinkUri();
    }

    public Manager finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExists {
        var ret = accountManager.getNewDeviceRegistration(tempIdentityKey);
        var number = ret.getNumber();

        logger.info("Received link information from {}, linking in progress ...", number);

        if (SignalAccount.userExists(pathConfig.getDataPath(), number) && !canRelinkExistingAccount(number)) {
            throw new UserAlreadyExists(number, SignalAccount.getFileName(pathConfig.getDataPath(), number));
        }

        var encryptedDeviceName = deviceName == null
                ? null
                : DeviceNameUtil.encryptDeviceName(deviceName, ret.getIdentity().getPrivateKey());

        var deviceId = accountManager.finishNewDeviceRegistration(ret.getProvisioningCode(),
                false,
                true,
                registrationId,
                encryptedDeviceName);

        // Create new account with the synced identity
        var profileKey = ret.getProfileKey() == null ? KeyUtils.createProfileKey() : ret.getProfileKey();

        SignalAccount account = null;
        try {
            account = SignalAccount.createOrUpdateLinkedAccount(pathConfig.getDataPath(),
                    number,
                    ret.getUuid(),
                    password,
                    encryptedDeviceName,
                    deviceId,
                    ret.getIdentity(),
                    registrationId,
                    profileKey);

            Manager m = null;
            try {
                m = new Manager(account, pathConfig, serviceEnvironmentConfig, userAgent);

                try {
                    m.refreshPreKeys();
                } catch (Exception e) {
                    logger.error("Failed to check new account state.");
                    throw e;
                }

                try {
                    m.requestAllSyncData();
                } catch (Exception e) {
                    logger.error("Failed to request sync messages from linked device.");
                    throw e;
                }

                final var result = m;
                account = null;
                m = null;

                return result;
            } finally {
                if (m != null) {
                    m.close();
                }
            }
        } finally {
            if (account != null) {
                account.close();
            }
        }
    }

    private boolean canRelinkExistingAccount(final String number) throws UserAlreadyExists, IOException {
        final SignalAccount signalAccount;
        try {
            signalAccount = SignalAccount.load(pathConfig.getDataPath(), number, false);
        } catch (IOException e) {
            logger.debug("Account in use or failed to load.", e);
            return false;
        }

        try (signalAccount) {
            if (signalAccount.isMasterDevice()) {
                logger.debug("Account is a master device.");
                return false;
            }

            final var m = new Manager(signalAccount, pathConfig, serviceEnvironmentConfig, userAgent);
            try (m) {
                m.checkAccountState();
            } catch (AuthorizationFailedException ignored) {
                return true;
            }

            logger.debug("Account is still successfully linked.");
            return false;
        }
    }
}
