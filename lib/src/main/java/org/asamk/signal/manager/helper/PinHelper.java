package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.Pair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;

public class PinHelper {

    private final static Logger logger = LoggerFactory.getLogger(PinHelper.class);

    private final KeyBackupService keyBackupService;
    private final Collection<KeyBackupService> fallbackKeyBackupServices;

    public PinHelper(
            final KeyBackupService keyBackupService, final Collection<KeyBackupService> fallbackKeyBackupServices
    ) {
        this.keyBackupService = keyBackupService;
        this.fallbackKeyBackupServices = fallbackKeyBackupServices;
    }

    public void setRegistrationLockPin(
            String pin, MasterKey masterKey
    ) throws IOException {
        final var pinChangeSession = keyBackupService.newPinChangeSession();
        final var hashedPin = PinHashUtil.hashPin(pin, pinChangeSession.hashSalt());

        try {
            pinChangeSession.setPin(hashedPin, masterKey);
        } catch (UnauthenticatedResponseException e) {
            throw new IOException(e);
        }
        pinChangeSession.enableRegistrationLock(masterKey);
    }

    public void migrateRegistrationLockPin(String pin, MasterKey masterKey) throws IOException {
        setRegistrationLockPin(pin, masterKey);

        for (final var keyBackupService : fallbackKeyBackupServices) {
            try {
                final var pinChangeSession = keyBackupService.newPinChangeSession();
                pinChangeSession.removePin();
            } catch (Exception e) {
                logger.warn("Failed to remove PIN from fallback KBS: {}", e.getMessage());
            }
        }
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
        var tokenResponsePair = getTokenResponse(basicStorageCredentials);
        final var tokenResponse = tokenResponsePair.first();
        final var keyBackupService = tokenResponsePair.second();

        var registrationLockData = restoreMasterKey(pin, basicStorageCredentials, tokenResponse, keyBackupService);
        if (registrationLockData == null) {
            throw new AssertionError("Failed to restore master key");
        }
        return registrationLockData;
    }

    private Pair<TokenResponse, KeyBackupService> getTokenResponse(String basicStorageCredentials) throws IOException {
        final var keyBackupServices = Stream.concat(Stream.of(keyBackupService), fallbackKeyBackupServices.stream())
                .toList();
        for (final var keyBackupService : keyBackupServices) {
            var tokenResponse = keyBackupService.getToken(basicStorageCredentials);
            if (tokenResponse != null && tokenResponse.getTries() > 0) {
                return new Pair<>(tokenResponse, keyBackupService);
            }
        }
        throw new IOException("KBS Account locked, maximum pin attempts reached.");
    }

    private KbsPinData restoreMasterKey(
            String pin,
            String basicStorageCredentials,
            TokenResponse tokenResponse,
            final KeyBackupService keyBackupService
    ) throws IOException, KeyBackupSystemNoDataException, KeyBackupServicePinException {
        if (pin == null) return null;

        if (basicStorageCredentials == null) {
            throw new AssertionError("Cannot restore KBS key, no storage credentials supplied");
        }

        var session = keyBackupService.newRegistrationSession(basicStorageCredentials, tokenResponse);

        try {
            var hashedPin = PinHashUtil.hashPin(pin, session.hashSalt());
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
