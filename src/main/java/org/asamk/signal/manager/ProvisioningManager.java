/*
  Copyright (C) 2015-2020 AsamK and contributors

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

import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ProvisioningManager {

    private final PathConfig pathConfig;
    private final SignalServiceConfiguration serviceConfiguration;
    private final String userAgent;

    private final SignalServiceAccountManager accountManager;
    private final IdentityKeyPair identityKey;
    private final int registrationId;
    private final String password;

    public ProvisioningManager(File settingsPath, SignalServiceConfiguration serviceConfiguration, String userAgent) {
        this.pathConfig = PathConfig.createDefault(settingsPath);
        this.serviceConfiguration = serviceConfiguration;
        this.userAgent = userAgent;

        identityKey = KeyUtils.generateIdentityKeyPair();
        registrationId = KeyHelper.generateRegistrationId(false);
        password = KeyUtils.createPassword();
        final SleepTimer timer = new UptimeSleepTimer();
        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration));
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        accountManager = new SignalServiceAccountManager(serviceConfiguration,
                new DynamicCredentialsProvider(null, null, password, null, SignalServiceAddress.DEFAULT_DEVICE_ID),
                userAgent,
                groupsV2Operations,
                timer);
    }

    public String getDeviceLinkUri() throws TimeoutException, IOException {
        String deviceUuid = accountManager.getNewDeviceUuid();

        return new DeviceLinkInfo(deviceUuid, identityKey.getPublicKey().getPublicKey()).createDeviceLinkUri();
    }

    public String finishDeviceLink(String deviceName) throws IOException, InvalidKeyException, TimeoutException, UserAlreadyExists {
        String signalingKey = KeyUtils.createSignalingKey();
        SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.finishNewDeviceRegistration(
                identityKey,
                signalingKey,
                false,
                true,
                registrationId,
                deviceName);

        String username = ret.getNumber();
        // TODO do this check before actually registering
        if (SignalAccount.userExists(pathConfig.getDataPath(), username)) {
            throw new UserAlreadyExists(username, SignalAccount.getFileName(pathConfig.getDataPath(), username));
        }

        // Create new account with the synced identity
        byte[] profileKeyBytes = ret.getProfileKey();
        ProfileKey profileKey;
        if (profileKeyBytes == null) {
            profileKey = KeyUtils.createProfileKey();
        } else {
            try {
                profileKey = new ProfileKey(profileKeyBytes);
            } catch (InvalidInputException e) {
                throw new IOException("Received invalid profileKey", e);
            }
        }

        try (SignalAccount account = SignalAccount.createLinkedAccount(pathConfig.getDataPath(),
                username,
                ret.getUuid(),
                password,
                ret.getDeviceId(),
                ret.getIdentity(),
                registrationId,
                signalingKey,
                profileKey)) {
            account.save();

            try (Manager m = new Manager(account, pathConfig, serviceConfiguration, userAgent)) {

                m.refreshPreKeys();

                m.requestSyncGroups();
                m.requestSyncContacts();
                m.requestSyncBlocked();
                m.requestSyncConfiguration();

                m.saveAccount();
            }
        }

        return username;
    }
}
