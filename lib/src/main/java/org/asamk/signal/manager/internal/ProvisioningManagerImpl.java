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
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironmentConfig;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.asamk.signal.manager.util.KeyUtils;
import org.signal.core.models.AccountEntropyPool;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.signal.core.models.backup.MediaRootBackupKey;
import org.signal.core.util.crypto.DeviceNameCipher;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.account.DeviceAttributes;
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher;
import org.whispersystems.signalservice.internal.push.ProvisionMessage;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function3;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;

import static org.asamk.signal.manager.util.KeyUtils.generatePreKeysForType;
import static org.asamk.signal.manager.util.Utils.handleResponseException;

public class ProvisioningManagerImpl implements ProvisioningManager, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ProvisioningManagerImpl.class);

    private final PathConfig pathConfig;
    private final ServiceEnvironmentConfig serviceEnvironmentConfig;
    private final String userAgent;
    private final Consumer<Manager> newManagerListener;
    private final AccountsStore accountsStore;

    private final String password;
    private final CompletableFuture<String> urlFuture = new CompletableFuture<>();
    private final CompletableFuture<SecondaryProvisioningCipher.ProvisioningDecryptResult<ProvisionMessage>> messageFuture = new CompletableFuture<>();
    private final Closeable socketHandle;

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

        final IdentityKeyPair tempIdentityKey = KeyUtils.generateIdentityKeyPair();
        password = KeyUtils.createPassword();

        socketHandle = ProvisioningSocket.Companion.start(new ProvisioningSocket.Mode.Link(false),
                tempIdentityKey,
                serviceEnvironmentConfig.signalServiceConfiguration(),
                (id, t) -> {
                    urlFuture.completeExceptionally(t);
                    messageFuture.completeExceptionally(t);
                },
                new ProvisioningBlock());
    }

    @Override
    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        try {
            var url = urlFuture.get(30, TimeUnit.SECONDS);
            return new URI(url);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("Timed out waiting for provisioning URL");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for provisioning URL", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to get provisioning URL", e.getCause());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid provisioning URL", e);
        }
    }

    @Override
    public String finishDeviceLink(String deviceName) throws IOException, TimeoutException, UserAlreadyExistsException {
        SecondaryProvisioningCipher.ProvisioningDecryptResult<ProvisionMessage> decryptResult;
        try {
            decryptResult = messageFuture.get(120, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("Timed out waiting for provisioning message");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for provisioning message", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to receive provisioning message", e.getCause());
        }

        if (!(decryptResult instanceof SecondaryProvisioningCipher.ProvisioningDecryptResult.Success<ProvisionMessage> success)) {
            throw new IOException("Failed to decrypt provisioning message");
        }
        var msg = success.getMessage();

        var number = msg.number;
        var aci = ACI.parseOrThrow(msg.aci, msg.aciBinary);
        var pni = PNI.parseOrThrow(msg.pni, msg.pniBinary);

        logger.info("Received link information from {}, linking in progress ...", number);

        var accountPath = accountsStore.getPathByAci(aci);
        if (accountPath == null) {
            accountPath = accountsStore.getPathByNumber(number);
        }
        final var accountExists = accountPath != null && SignalAccount.accountFileExists(pathConfig.dataPath(),
                accountPath);
        if (accountExists && !canRelinkExistingAccount(accountPath)) {
            throw new UserAlreadyExistsException(number, SignalAccount.getFileName(pathConfig.dataPath(), accountPath));
        }
        if (accountPath == null) {
            accountPath = accountsStore.addAccount(number, aci);
        } else {
            accountsStore.updateAccount(accountPath, number, aci);
        }

        final IdentityKeyPair aciIdentity;
        final IdentityKeyPair pniIdentity;
        final ProfileKey profileKey;
        try {
            aciIdentity = new IdentityKeyPair(new IdentityKey(msg.aciIdentityKeyPublic.toByteArray()),
                    new ECPrivateKey(msg.aciIdentityKeyPrivate.toByteArray()));
            pniIdentity = new IdentityKeyPair(new IdentityKey(msg.pniIdentityKeyPublic.toByteArray()),
                    new ECPrivateKey(msg.pniIdentityKeyPrivate.toByteArray()));
            profileKey = msg.profileKey == null
                    ? KeyUtils.createProfileKey()
                    : new ProfileKey(msg.profileKey.toByteArray());
        } catch (Exception e) {
            throw new IOException("Invalid key material in provisioning message", e);
        }

        var encryptedDeviceName = deviceName == null
                ? null
                : DeviceNameCipher.encryptDeviceName(deviceName.getBytes(StandardCharsets.UTF_8), aciIdentity);
        var accountEntropyPool = msg.accountEntropyPool == null ? null : new AccountEntropyPool(msg.accountEntropyPool);
        var mediaRootBackupKey = msg.mediaRootBackupKey == null
                ? null
                : new MediaRootBackupKey(msg.mediaRootBackupKey.toByteArray());

        SignalAccount account = null;
        var cleanUpPartialAccountOnFailure = false;
        var linkingFinished = false;
        try {
            if (!accountExists) {
                account = SignalAccount.createLinkedAccount(pathConfig.dataPath(),
                        accountPath,
                        serviceEnvironmentConfig.type(),
                        Settings.DEFAULT);
                cleanUpPartialAccountOnFailure = true;
            } else {
                account = SignalAccount.load(pathConfig.dataPath(), accountPath, true, Settings.DEFAULT);
                cleanUpPartialAccountOnFailure = false;
            }

            account.setProvisioningData(number,
                    aci,
                    pni,
                    password,
                    encryptedDeviceName,
                    aciIdentity,
                    pniIdentity,
                    profileKey,
                    accountEntropyPool,
                    msg.authCredentialSalt == null ? null : msg.authCredentialSalt.toByteArray(),
                    mediaRootBackupKey);

            if (msg.readReceipts != null) {
                account.getConfigurationStore().setReadReceipts(msg.readReceipts);
            }

            final var aciPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.ACI));
            final var pniPreKeys = generatePreKeysForType(account.getAccountData(ServiceIdType.PNI));

            logger.debug("Finishing new device registration");
            final var attrs = account.getAccountAttributes(null);
            final var deviceAttributes = new DeviceAttributes(attrs.getFetchesMessages(),
                    attrs.getRegistrationId(),
                    attrs.getPniRegistrationId(),
                    attrs.getName(),
                    attrs.getCapabilities());
            final var unauthAccountManager = SignalServiceAccountManager.createWithStaticCredentials(
                    serviceEnvironmentConfig.signalServiceConfiguration(),
                    null,
                    null,
                    number,
                    SignalServiceAddress.DEFAULT_DEVICE_ID,
                    password,
                    userAgent,
                    ServiceConfig.AUTOMATIC_NETWORK_RETRY,
                    ServiceConfig.GROUP_MAX_SIZE);
            final var registerResponse = handleResponseException(unauthAccountManager.getRegistrationApi()
                    .registerAsSecondaryDevice(msg.provisioningCode, deviceAttributes, aciPreKeys, pniPreKeys, null));
            final var deviceId = Integer.parseInt(registerResponse.getDeviceId());

            account.finishLinking(deviceId, aciPreKeys, pniPreKeys);
            linkingFinished = true;

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
        } catch (Exception e) {
            if (!linkingFinished && cleanUpPartialAccountOnFailure && account != null) {
                cleanupPartialAccount(account, accountPath, e);
                account = null;
            }
            throw e;
        } finally {
            if (account != null) {
                account.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        socketHandle.close();
    }

    private void cleanupPartialAccount(final SignalAccount account, final String accountPath, final Exception cause) {
        logger.warn("Link attempt failed before registration completed, removing partial account state for {}.",
                accountPath,
                cause);
        try {
            account.deleteAccountData();
        } catch (IOException cleanupError) {
            logger.warn("Failed to delete partial account data for {}: {}",
                    accountPath,
                    cleanupError.getMessage(),
                    cleanupError);
        }
        try {
            accountsStore.removeAccount(accountPath);
        } catch (RuntimeException cleanupError) {
            logger.warn("Failed to remove partial account entry for {}: {}",
                    accountPath,
                    cleanupError.getMessage(),
                    cleanupError);
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
            if (signalAccount.getDeviceId() <= 0) {
                logger.debug("Account has invalid deviceId {}, allowing relink.", signalAccount.getDeviceId());
                return true;
            }
            if (signalAccount.isPrimaryDevice()) {
                logger.debug("Account is a primary device.");
                return false;
            }
            if (signalAccount.isRegistered()
                    && signalAccount.getServiceEnvironment() != null
                    && signalAccount.getServiceEnvironment() != serviceEnvironmentConfig.type()) {
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

    private class ProvisioningBlock implements Function3<CoroutineScope, ProvisioningSocket<ProvisionMessage>, Continuation<? super Unit>, Object> {

        @Override
        public Object invoke(
                CoroutineScope scope,
                ProvisioningSocket<ProvisionMessage> socket,
                Continuation<? super Unit> cont
        ) {
            Thread.ofVirtual().start(() -> {
                try {
                    urlFuture.complete(BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                            (s, c) -> socket.getProvisioningUrl(c)));
                    messageFuture.complete(BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                            (s, c) -> socket.getProvisioningMessageDecryptResult(c)));
                    cont.resumeWith(Unit.INSTANCE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failBoth(new RuntimeException(e), cont);
                } catch (Throwable t) {
                    failBoth(t, cont);
                }
            });
            return IntrinsicsKt.getCOROUTINE_SUSPENDED();
        }

        private void failBoth(Throwable t, Continuation<? super Unit> cont) {
            urlFuture.completeExceptionally(t);
            messageFuture.completeExceptionally(t);
            cont.resumeWith(ResultKt.createFailure(t));
        }
    }
}
