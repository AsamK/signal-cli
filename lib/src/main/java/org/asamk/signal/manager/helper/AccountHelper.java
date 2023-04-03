package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.DeviceLinkInfo;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.InvalidDeviceLinkException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.manager.util.NumberVerificationUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.usernames.BaseUsernameException;
import org.signal.libsignal.usernames.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        final var aci = ACI.parseOrNull(whoAmI.getAci());
        final var pni = PNI.parseOrNull(whoAmI.getPni());
        if (number.equals(account.getNumber()) && aci.equals(account.getAci()) && pni.equals(account.getPni())) {
            return;
        }

        updateSelfIdentifiers(number, aci, pni);
    }

    private void updateSelfIdentifiers(final String number, final ACI aci, final PNI pni) {
        account.setNumber(number);
        account.setAci(aci);
        account.setPni(pni);
        if (account.isPrimaryDevice() && account.getPniIdentityKeyPair() == null && account.getPni() != null) {
            account.setPniIdentityKeyPair(KeyUtils.generateIdentityKeyPair());
        }
        account.getRecipientTrustedResolver().resolveSelfRecipientTrusted(account.getSelfRecipientAddress());
        // TODO check and update remote storage
        context.getUnidentifiedAccessHelper().rotateSenderCertificates();
        dependencies.resetAfterAddressChange();
        context.getAccountFileUpdater().updateAccountIdentifiers(account.getNumber(), account.getAci());
    }

    public void setPni(
            final PNI updatedPni,
            final IdentityKeyPair pniIdentityKeyPair,
            final SignedPreKeyRecord pniSignedPreKey,
            final int localPniRegistrationId
    ) throws IOException {
        account.setPni(updatedPni, pniIdentityKeyPair, pniSignedPreKey, localPniRegistrationId);
        context.getPreKeyHelper().refreshPreKeysIfNecessary(ServiceIdType.PNI);
        if (account.getPni() == null || !account.getPni().equals(updatedPni)) {
            context.getGroupV2Helper().clearAuthCredentialCache();
        }
    }

    public void startChangeNumber(
            String newNumber, String captcha, boolean voiceVerification
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
        // TODO create new PNI identity key
        final List<OutgoingPushMessage> deviceMessages = null;
        final Map<String, SignedPreKeyEntity> devicePniSignedPreKeys = null;
        final Map<String, Integer> pniRegistrationIds = null;
        var sessionId = account.getSessionId(account.getNumber());
        final var result = NumberVerificationUtils.verifyNumber(sessionId,
                verificationCode,
                pin,
                context.getPinHelper(),
                (sessionId1, verificationCode1, registrationLock) -> {
                    final var accountManager = dependencies.getAccountManager();
                    try {
                        Utils.handleResponseException(accountManager.verifyAccount(verificationCode, sessionId1));
                    } catch (AlreadyVerifiedException e) {
                        // Already verified so can continue changing number
                    }
                    return Utils.handleResponseException(accountManager.changeNumber(new ChangePhoneNumberRequest(
                            sessionId1,
                            null,
                            newNumber,
                            registrationLock,
                            account.getPniIdentityKeyPair().getPublicKey(),
                            deviceMessages,
                            devicePniSignedPreKeys,
                            pniRegistrationIds)));
                });
        // TODO handle response
        updateSelfIdentifiers(newNumber, account.getAci(), PNI.parseOrThrow(result.first().getPni()));
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

        final var candidates = Username.generateCandidates(nickname, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH);
        final var candidateHashes = new ArrayList<String>();
        for (final var candidate : candidates) {
            candidateHashes.add(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(candidate)));
        }

        final var response = dependencies.getAccountManager().reserveUsername(candidateHashes);
        final var hashIndex = candidateHashes.indexOf(response.getUsernameHash());
        if (hashIndex == -1) {
            logger.warn("[reserveUsername] The response hash could not be found in our set of candidateHashes.");
            throw new IOException("Unexpected username response");
        }

        logger.debug("[reserveUsername] Successfully reserved username.");
        final var username = candidates.get(hashIndex);

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
        final var localUsernameHash = Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(localUsername));

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

    public void addDevice(DeviceLinkInfo deviceLinkInfo) throws IOException, InvalidDeviceLinkException {
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
    }

    public void setRegistrationPin(String pin) throws IOException {
        var masterKey = account.getOrCreatePinMasterKey();

        context.getPinHelper().setRegistrationLockPin(pin, masterKey);

        account.setRegistrationLockPin(pin);
    }

    public void removeRegistrationPin() throws IOException {
        // Remove KBS Pin
        context.getPinHelper().removeRegistrationLockPin();

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
