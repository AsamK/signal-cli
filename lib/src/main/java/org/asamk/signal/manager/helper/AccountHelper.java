package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.DeviceLinkUrl;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.NumberVerificationUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.core.util.Base64;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.SyncMessage;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okio.ByteString;

import static org.asamk.signal.manager.config.ServiceConfig.PREKEY_MAXIMUM_ID;
import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

public class AccountHelper {

    private final static Logger logger = LoggerFactory.getLogger(AccountHelper.class);

    private final Context context;
    private final SignalAccount account;
    private final SignalDependencies dependencies;

    private Callable unregisteredListener;

    public AccountHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public void setUnregisteredListener(final Callable unregisteredListener) {
        this.unregisteredListener = unregisteredListener;
    }

    public void checkAccountState() throws IOException {
        if (account.getLastReceiveTimestamp() == 0) {
            logger.info("The Signal protocol expects that incoming messages are regularly received.");
        } else {
            var diffInMilliseconds = System.currentTimeMillis() - account.getLastReceiveTimestamp();
            long days = TimeUnit.DAYS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS);
            if (days > 7) {
                logger.warn(
                        "Messages have been last received {} days ago. The Signal protocol expects that incoming messages are regularly received.",
                        days);
            }
        }
        try {
            updateAccountAttributes();
            context.getPreKeyHelper().refreshPreKeysIfNecessary();
            if (account.getAci() == null || account.getPni() == null) {
                checkWhoAmiI();
            }
            if (!account.isPrimaryDevice() && account.getPniIdentityKeyPair() == null) {
                context.getSyncHelper().requestSyncPniIdentity();
            }
            if (account.getPreviousStorageVersion() < 4
                    && account.isPrimaryDevice()
                    && account.getRegistrationLockPin() != null) {
                migrateRegistrationPin();
            }
        } catch (DeprecatedVersionException e) {
            logger.debug("Signal-Server returned deprecated version exception", e);
            throw e;
        } catch (AuthorizationFailedException e) {
            account.setRegistered(false);
            throw e;
        }
    }

    public void checkWhoAmiI() throws IOException {
        final var whoAmI = dependencies.getAccountManager().getWhoAmI();
        final var number = whoAmI.getNumber();
        final var aci = ACI.parseOrThrow(whoAmI.getAci());
        final var pni = PNI.parseOrThrow(whoAmI.getPni());
        if (number.equals(account.getNumber()) && aci.equals(account.getAci()) && pni.equals(account.getPni())) {
            return;
        }

        updateSelfIdentifiers(number, aci, pni);
    }

    private void updateSelfIdentifiers(final String number, final ACI aci, final PNI pni) {
        account.setNumber(number);
        account.setAci(aci);
        account.setPni(pni);
        if (account.isPrimaryDevice() && account.getPniIdentityKeyPair() == null) {
            account.setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
        }
        account.getRecipientTrustedResolver().resolveSelfRecipientTrusted(account.getSelfRecipientAddress());
        // TODO check and update remote storage
        context.getUnidentifiedAccessHelper().rotateSenderCertificates();
        dependencies.resetAfterAddressChange();
        context.getGroupV2Helper().clearAuthCredentialCache();
        context.getAccountFileUpdater().updateAccountIdentifiers(account.getNumber(), account.getAci());
    }

    public void setPni(
            final PNI updatedPni,
            final IdentityKeyPair pniIdentityKeyPair,
            final String number,
            final int localPniRegistrationId,
            final SignedPreKeyRecord pniSignedPreKey,
            final KyberPreKeyRecord lastResortKyberPreKey
    ) throws IOException {
        updateSelfIdentifiers(number != null ? number : account.getNumber(), account.getAci(), updatedPni);
        account.setNewPniIdentity(pniIdentityKeyPair, pniSignedPreKey, lastResortKyberPreKey, localPniRegistrationId);
        context.getPreKeyHelper().refreshPreKeysIfNecessary(ServiceIdType.PNI);
    }

    public void startChangeNumber(
            String newNumber, boolean voiceVerification, String captcha
    ) throws IOException, CaptchaRequiredException, NonNormalizedPhoneNumberException, RateLimitException {
        final var accountManager = dependencies.createUnauthenticatedAccountManager(newNumber, account.getPassword());
        String sessionId = NumberVerificationUtils.handleVerificationSession(accountManager,
                account.getSessionId(newNumber),
                id -> account.setSessionId(newNumber, id),
                voiceVerification,
                captcha);
        NumberVerificationUtils.requestVerificationCode(accountManager, sessionId, voiceVerification);
    }

    public void finishChangeNumber(
            String newNumber, String verificationCode, String pin
    ) throws IncorrectPinException, PinLockedException, IOException {
        for (var attempts = 0; attempts < 5; attempts++) {
            try {
                finishChangeNumberInternal(newNumber, verificationCode, pin);
                break;
            } catch (MismatchedDevicesException e) {
                logger.debug("Change number failed with mismatched devices, retrying.");
                try {
                    dependencies.getMessageSender().handleChangeNumberMismatchDevices(e.getMismatchedDevices());
                } catch (UntrustedIdentityException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
    }

    private void finishChangeNumberInternal(
            String newNumber, String verificationCode, String pin
    ) throws IncorrectPinException, PinLockedException, IOException {
        final var pniIdentity = KeyUtils.generateIdentityKeyPair();
        final var encryptedDeviceMessages = new ArrayList<OutgoingPushMessage>();
        final var devicePniSignedPreKeys = new HashMap<Integer, SignedPreKeyEntity>();
        final var devicePniLastResortKyberPreKeys = new HashMap<Integer, KyberPreKeyEntity>();
        final var pniRegistrationIds = new HashMap<Integer, Integer>();

        final var selfDeviceId = account.getDeviceId();
        SyncMessage.PniChangeNumber selfChangeNumber = null;

        final var deviceIds = new ArrayList<Integer>();
        deviceIds.add(SignalServiceAddress.DEFAULT_DEVICE_ID);
        final var aci = account.getAci();
        final var accountDataStore = account.getSignalServiceDataStore().aci();
        final var subDeviceSessions = accountDataStore.getSubDeviceSessions(aci.toString())
                .stream()
                .filter(deviceId -> accountDataStore.containsSession(new SignalProtocolAddress(aci.toString(),
                        deviceId)))
                .toList();
        deviceIds.addAll(subDeviceSessions);

        final var messageSender = dependencies.getMessageSender();
        for (final var deviceId : deviceIds) {
            // Signed Prekey
            final var signedPreKeyRecord = KeyUtils.generateSignedPreKeyRecord(KeyUtils.getRandomInt(PREKEY_MAXIMUM_ID),
                    pniIdentity.getPrivateKey());
            final var signedPreKeyEntity = new SignedPreKeyEntity(signedPreKeyRecord.getId(),
                    signedPreKeyRecord.getKeyPair().getPublicKey(),
                    signedPreKeyRecord.getSignature());
            devicePniSignedPreKeys.put(deviceId, signedPreKeyEntity);

            // Last-resort kyber prekey
            final var lastResortKyberPreKeyRecord = KeyUtils.generateKyberPreKeyRecord(KeyUtils.getRandomInt(
                    PREKEY_MAXIMUM_ID), pniIdentity.getPrivateKey());
            final var kyberPreKeyEntity = new KyberPreKeyEntity(lastResortKyberPreKeyRecord.getId(),
                    lastResortKyberPreKeyRecord.getKeyPair().getPublicKey(),
                    lastResortKyberPreKeyRecord.getSignature());
            devicePniLastResortKyberPreKeys.put(deviceId, kyberPreKeyEntity);

            // Registration Id
            var pniRegistrationId = -1;
            while (pniRegistrationId < 0 || pniRegistrationIds.containsValue(pniRegistrationId)) {
                pniRegistrationId = KeyHelper.generateRegistrationId(false);
            }
            pniRegistrationIds.put(deviceId, pniRegistrationId);

            // Device Message
            final var pniChangeNumber = new SyncMessage.PniChangeNumber.Builder().identityKeyPair(ByteString.of(
                            pniIdentity.serialize()))
                    .signedPreKey(ByteString.of(signedPreKeyRecord.serialize()))
                    .lastResortKyberPreKey(ByteString.of(lastResortKyberPreKeyRecord.serialize()))
                    .registrationId(pniRegistrationId)
                    .newE164(newNumber)
                    .build();

            if (deviceId == selfDeviceId) {
                selfChangeNumber = pniChangeNumber;
            } else {
                try {
                    final var message = messageSender.getEncryptedSyncPniInitializeDeviceMessage(deviceId,
                            pniChangeNumber);
                    encryptedDeviceMessages.add(message);
                } catch (UntrustedIdentityException | IOException | InvalidKeyException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        final var sessionId = account.getSessionId(newNumber);
        final var result = NumberVerificationUtils.verifyNumber(sessionId,
                verificationCode,
                pin,
                context.getPinHelper(),
                (sessionId1, verificationCode1, registrationLock) -> {
                    final var accountManager = dependencies.getAccountManager();
                    try {
                        Utils.handleResponseException(accountManager.verifyAccount(verificationCode1, sessionId1));
                    } catch (AlreadyVerifiedException e) {
                        // Already verified so can continue changing number
                    }
                    return Utils.handleResponseException(accountManager.changeNumber(new ChangePhoneNumberRequest(
                            sessionId1,
                            null,
                            newNumber,
                            registrationLock,
                            pniIdentity.getPublicKey(),
                            encryptedDeviceMessages,
                            Utils.mapKeys(devicePniSignedPreKeys, Object::toString),
                            Utils.mapKeys(devicePniLastResortKyberPreKeys, Object::toString),
                            Utils.mapKeys(pniRegistrationIds, Object::toString))));
                });

        final var updatePni = PNI.parseOrThrow(result.first().getPni());
        if (updatePni.equals(account.getPni())) {
            logger.debug("PNI is unchanged after change number");
            return;
        }

        handlePniChangeNumberMessage(selfChangeNumber, updatePni);
    }

    public void handlePniChangeNumberMessage(
            final SyncMessage.PniChangeNumber pniChangeNumber, final PNI updatedPni
    ) {
        if (pniChangeNumber.identityKeyPair != null
                && pniChangeNumber.registrationId != null
                && pniChangeNumber.signedPreKey != null) {
            logger.debug("New PNI: {}", updatedPni);
            try {
                setPni(updatedPni,
                        new IdentityKeyPair(pniChangeNumber.identityKeyPair.toByteArray()),
                        pniChangeNumber.newE164,
                        pniChangeNumber.registrationId,
                        new SignedPreKeyRecord(pniChangeNumber.signedPreKey.toByteArray()),
                        pniChangeNumber.lastResortKyberPreKey != null
                                ? new KyberPreKeyRecord(pniChangeNumber.lastResortKyberPreKey.toByteArray())
                                : null);
            } catch (Exception e) {
                logger.warn("Failed to handle change number message", e);
            }
        }
    }

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 32;

    public String reserveUsername(String nickname) throws IOException, BaseUsernameException {
        final var currentUsername = account.getUsername();
        if (currentUsername != null) {
            final var currentNickname = currentUsername.substring(0, currentUsername.indexOf('.'));
            if (currentNickname.equals(nickname)) {
                refreshCurrentUsername();
                return currentUsername;
            }
        }

        final var candidates = Username.candidatesFrom(nickname, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH);
        final var candidateHashes = new ArrayList<String>();
        for (final var candidate : candidates) {
            candidateHashes.add(Base64.encodeUrlSafeWithoutPadding(candidate.getHash()));
        }

        final var response = dependencies.getAccountManager().reserveUsername(candidateHashes);
        final var hashIndex = candidateHashes.indexOf(response.getUsernameHash());
        if (hashIndex == -1) {
            logger.warn("[reserveUsername] The response hash could not be found in our set of candidateHashes.");
            throw new IOException("Unexpected username response");
        }

        logger.debug("[reserveUsername] Successfully reserved username.");
        final var username = candidates.get(hashIndex).getUsername();

        dependencies.getAccountManager().confirmUsername(username, response);
        account.setUsername(username);
        account.getRecipientStore().resolveSelfRecipientTrusted(account.getSelfRecipientAddress());
        logger.debug("[confirmUsername] Successfully confirmed username.");

        return username;
    }

    public void refreshCurrentUsername() throws IOException, BaseUsernameException {
        final var localUsername = account.getUsername();
        if (localUsername == null) {
            return;
        }

        final var whoAmIResponse = dependencies.getAccountManager().getWhoAmI();
        final var serverUsernameHash = whoAmIResponse.getUsernameHash();
        final var hasServerUsername = !isEmpty(serverUsernameHash);
        final var localUsernameHash = Base64.encodeUrlSafeWithoutPadding(new Username(localUsername).getHash());

        if (!hasServerUsername) {
            logger.debug("No remote username is set.");
        }

        if (!Objects.equals(localUsernameHash, serverUsernameHash)) {
            logger.debug("Local username hash does not match server username hash.");
        }

        if (!hasServerUsername || !Objects.equals(localUsernameHash, serverUsernameHash)) {
            logger.debug("Attempting to resynchronize username.");
            tryReserveConfirmUsername(localUsername, localUsernameHash);
        } else {
            logger.debug("Username already set, not refreshing.");
        }
    }

    private void tryReserveConfirmUsername(final String username, String localUsernameHash) throws IOException {
        final var response = dependencies.getAccountManager().reserveUsername(List.of(localUsernameHash));
        logger.debug("[reserveUsername] Successfully reserved existing username.");
        dependencies.getAccountManager().confirmUsername(username, response);
        logger.debug("[confirmUsername] Successfully confirmed existing username.");
    }

    public void deleteUsername() throws IOException {
        dependencies.getAccountManager().deleteUsername();
        account.setUsername(null);
        logger.debug("[deleteUsername] Successfully deleted the username.");
    }

    public void setDeviceName(String deviceName) {
        final var privateKey = account.getAciIdentityKeyPair().getPrivateKey();
        final var encryptedDeviceName = DeviceNameUtil.encryptDeviceName(deviceName, privateKey);
        account.setEncryptedDeviceName(encryptedDeviceName);
    }

    public void updateAccountAttributes() throws IOException {
        dependencies.getAccountManager().setAccountAttributes(account.getAccountAttributes(null));
    }

    public void addDevice(DeviceLinkUrl deviceLinkInfo) throws IOException, InvalidDeviceLinkException {
        var verificationCode = dependencies.getAccountManager().getNewDeviceVerificationCode();

        try {
            dependencies.getAccountManager()
                    .addDevice(deviceLinkInfo.deviceIdentifier(),
                            deviceLinkInfo.deviceKey(),
                            account.getAciIdentityKeyPair(),
                            account.getPniIdentityKeyPair(),
                            account.getProfileKey(),
                            verificationCode);
        } catch (InvalidKeyException e) {
            throw new InvalidDeviceLinkException("Invalid device link", e);
        }
        account.setMultiDevice(true);
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        dependencies.getAccountManager().removeDevice(deviceId);
        var devices = dependencies.getAccountManager().getDevices();
        account.setMultiDevice(devices.size() > 1);
    }

    public void migrateRegistrationPin() throws IOException {
        var masterKey = account.getOrCreatePinMasterKey();

        context.getPinHelper().migrateRegistrationLockPin(account.getRegistrationLockPin(), masterKey);
        dependencies.getAccountManager().enableRegistrationLock(masterKey);
    }

    public void setRegistrationPin(String pin) throws IOException {
        var masterKey = account.getOrCreatePinMasterKey();

        context.getPinHelper().setRegistrationLockPin(pin, masterKey);
        dependencies.getAccountManager().enableRegistrationLock(masterKey);

        account.setRegistrationLockPin(pin);
    }

    public void removeRegistrationPin() throws IOException {
        // Remove KBS Pin
        context.getPinHelper().removeRegistrationLockPin();
        dependencies.getAccountManager().disableRegistrationLock();

        account.setRegistrationLockPin(null);
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the primary device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        dependencies.getAccountManager().setGcmId(Optional.empty());

        account.setRegistered(false);
        unregisteredListener.call();
    }

    public void deleteAccount() throws IOException {
        try {
            context.getPinHelper().removeRegistrationLockPin();
        } catch (IOException e) {
            logger.warn("Failed to remove registration lock pin");
        }
        account.setRegistrationLockPin(null);

        dependencies.getAccountManager().deleteAccount();

        account.setRegistered(false);
        unregisteredListener.call();
    }

    public interface Callable {

        void call();
    }
}
