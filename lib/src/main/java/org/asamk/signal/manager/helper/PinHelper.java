package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.util.PinHashing;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;

public class PinHelper {

    private final KeyBackupService keyBackupService;

    public PinHelper(final KeyBackupService keyBackupService) {
        this.keyBackupService = keyBackupService;
    }

    public void setRegistrationLockPin(
            String pin, MasterKey masterKey
    ) throws IOException {
        final var pinChangeSession = keyBackupService.newPinChangeSession();
        final var hashedPin = PinHashing.hashPin(pin, pinChangeSession);

        try {
            pinChangeSession.setPin(hashedPin, masterKey);
        } catch (UnauthenticatedResponseException e) {
            throw new IOException(e);
        }
        pinChangeSession.enableRegistrationLock(masterKey);
    }

    public void removeRegistrationLockPin() throws IOException {
        final var pinChangeSession = keyBackupService.newPinChangeSession();
        pinChangeSession.disableRegistrationLock();
        try {
            pinChangeSession.removePin();
        } catch (UnauthenticatedResponseException e) {
            throw new IOException(e);
        }
    }

    public KbsPinData getRegistrationLockData(
            String pin, LockedException e
    ) throws IOException, IncorrectPinException {
        var basicStorageCredentials = e.getBasicStorageCredentials();
        if (basicStorageCredentials == null) {
            return null;
        }

        try {
            return getRegistrationLockData(pin, basicStorageCredentials);
        } catch (KeyBackupSystemNoDataException ex) {
            throw new IOException(e);
        } catch (KeyBackupServicePinException ex) {
            throw new IncorrectPinException(ex.getTriesRemaining());
        }
    }

    private KbsPinData getRegistrationLockData(
            String pin, String basicStorageCredentials
    ) throws IOException, KeyBackupSystemNoDataException, KeyBackupServicePinException {
        var tokenResponse = keyBackupService.getToken(basicStorageCredentials);
        if (tokenResponse == null || tokenResponse.getTries() == 0) {
            throw new IOException("KBS Account locked, maximum pin attempts reached.");
        }

        var registrationLockData = restoreMasterKey(pin, basicStorageCredentials, tokenResponse);
        if (registrationLockData == null) {
            throw new AssertionError("Failed to restore master key");
        }
        return registrationLockData;
    }

    private KbsPinData restoreMasterKey(
            String pin, String basicStorageCredentials, TokenResponse tokenResponse
    ) throws IOException, KeyBackupSystemNoDataException, KeyBackupServicePinException {
        if (pin == null) return null;

        if (basicStorageCredentials == null) {
            throw new AssertionError("Cannot restore KBS key, no storage credentials supplied");
        }

        var session = keyBackupService.newRegistrationSession(basicStorageCredentials, tokenResponse);

        try {
            var hashedPin = PinHashing.hashPin(pin, session);
            var kbsData = session.restorePin(hashedPin);
            if (kbsData == null) {
                throw new AssertionError("Null not expected");
            }
            return kbsData;
        } catch (UnauthenticatedResponseException | InvalidKeyException e) {
            throw new IOException(e);
        }
    }
}
