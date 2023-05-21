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
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.push.ConfirmCodeMessage;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.asamk.signal.manager.config.ServiceConfig.getCapabilities;

public class ProvisioningManagerImpl implements ProvisioningManager {

    private final static Logger logger = LoggerFactory.getLogger(ProvisioningManagerImpl.class);

    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final Consumer<Manager> newManagerListener;
    private final AccountsStore accountsStore;

    private final SignalServiceAccountManager accountManager;
    private final IdentityKeyPair tempIdentityKey;
    private final int registrationId;
    private final int pniRegistrationId;
    private final String password;

    public ProvisioningManagerImpl(
            PathConfig pathConfig,
            ServiceEnvironmentConfig serviceEnvironmentConfig,
            String userAgent,
            final Consumer<Manager> newManagerListener,
            final AccountsStore accountsStore
    ) {
        this.pathConfig = pathConfig;
        this.serviceEnvironmentConfig = serviceEnvironmentConfig;
        this.userAgent = userAgent;
        this.newManagerListener = newManagerListener;
        this.accountsStore = accountsStore;

        tempIdentityKey = KeyUtils.generateIdentityKeyPair();
        registrationId = KeyHelper.generateRegistrationId(false);
        pniRegistrationId = KeyHelper.generateRegistrationId(false);
        password = KeyUtils.createPassword();
        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceEnvironmentConfig.getSignalServiceConfiguration()),
                    ServiceConfig.GROUP_MAX_SIZE);
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        accountManager = new SignalServiceAccountManager(serviceEnvironmentConfig.getSignalServiceConfiguration(),
                new DynamicCredentialsProvider(null, null, null, password, SignalServiceAddress.DEFAULT_DEVICE_ID),
                userAgent,
                groupsV2Operations,
                ServiceConfig.AUTOMATIC_NETWORK_RETRY);
    }

    @Override
    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        var deviceUuid = accountManager.getNewDeviceUuid();

        return new DeviceLinkUrl(deviceUuid, tempIdentityKey.getPublicKey().getPublicKey()).createDeviceLinkUri();
    }

    @Override
    public String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException {
        var ret = accountManager.getNewDeviceRegistration(tempIdentityKey);
        var number = ret.getNumber();
        var aci = ret.getAci();
        var pni = ret.getPni();

        logger.info("Received link information from {}, linking in progress ...", number);

        var accountPath = accountsStore.getPathByAci(aci);
        if (accountPath == null) {
            accountPath = accountsStore.getPathByNumber(number);
        }
        if (accountPath != null
                && SignalAccount.accountFileExists(pathConfig.dataPath(), accountPath)
                && !canRelinkExistingAccount(accountPath)) {
            throw new UserAlreadyExistsException(number, SignalAccount.getFileName(pathConfig.dataPath(), accountPath));
        }
        if (accountPath == null) {
            accountPath = accountsStore.addAccount(number, aci);
        } else {
            accountsStore.updateAccount(accountPath, number, aci);
        }

        var encryptedDeviceName = deviceName == null
                ? null
                : DeviceNameUtil.encryptDeviceName(deviceName, ret.getAciIdentity().getPrivateKey());

        logger.debug("Finishing new device registration");
        var deviceId = accountManager.finishNewDeviceRegistration(ret.getProvisioningCode(),
                new ConfirmCodeMessage(false,
                        true,
                        registrationId,
                        pniRegistrationId,
                        encryptedDeviceName,
                        getCapabilities(false)));

        // Create new account with the synced identity
        var profileKey = ret.getProfileKey() == null ? KeyUtils.createProfileKey() : ret.getProfileKey();

        SignalAccount account = null;
        try {
            account = SignalAccount.createOrUpdateLinkedAccount(pathConfig.dataPath(),
                    accountPath,
                    number,
                    serviceEnvironmentConfig.getType(),
                    aci,
                    pni,
                    password,
                    encryptedDeviceName,
                    deviceId,
                    ret.getAciIdentity(),
                    ret.getPniIdentity(),
                    registrationId,
                    pniRegistrationId,
                    profileKey,
                    Settings.DEFAULT);
            account.getConfigurationStore().setReadReceipts(ret.isReadReceipts());

            ManagerImpl m = null;
            try {
                m = new ManagerImpl(account,
                        pathConfig,
                        new AccountFileUpdaterImpl(accountsStore, accountPath),
                        serviceEnvironmentConfig,
                        userAgent);
                account = null;

                logger.debug("Refreshing pre keys");
                try {
                    m.refreshPreKeys();
                } catch (Exception e) {
                    logger.error("Failed to refresh pre keys.", e);
                }

                logger.debug("Requesting sync data");
                try {
                    m.requestAllSyncData();
                } catch (Exception e) {
                    logger.error(
                            "Failed to request sync messages from linked device, data can be requested again with `sendSyncRequest`.",
                            e);
                }

                if (newManagerListener != null) {
                    newManagerListener.accept(m);
                    m = null;
                }
                return number;
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

    private boolean canRelinkExistingAccount(final String accountPath) throws IOException {
        final SignalAccount signalAccount;
        try {
            signalAccount = SignalAccount.load(pathConfig.dataPath(), accountPath, false, Settings.DEFAULT);
        } catch (IOException e) {
            logger.debug("Account in use or failed to load.", e);
            return false;
        } catch (OverlappingFileLockException e) {
            logger.debug("Account in use.", e);
            return false;
        }

        try (signalAccount) {
            if (signalAccount.isPrimaryDevice()) {
                logger.debug("Account is a primary device.");
                return false;
            }
            if (signalAccount.isRegistered()
                    && signalAccount.getServiceEnvironment() != null
                    && signalAccount.getServiceEnvironment() != serviceEnvironmentConfig.getType()) {
                logger.debug("Account is registered in another environment: {}.",
                        signalAccount.getServiceEnvironment());
                return false;
            }

            final var m = new ManagerImpl(signalAccount,
                    pathConfig,
                    new AccountFileUpdaterImpl(accountsStore, accountPath),
                    serviceEnvironmentConfig,
                    userAgent);
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
