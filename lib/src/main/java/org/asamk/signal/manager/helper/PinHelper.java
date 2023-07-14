package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV1;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.util.Collection;

public class PinHelper {

    private final static Logger logger = LoggerFactory.getLogger(PinHelper.class);

    private final KeyBackupService keyBackupService;
    private final SecureValueRecoveryV1 secureValueRecoveryV1;
    private final SecureValueRecoveryV2 secureValueRecoveryV2;
    private final Collection<KeyBackupService> fallbackKeyBackupServices;

    public PinHelper(
            final KeyBackupService keyBackupService,
            final Collection<KeyBackupService> fallbackKeyBackupServices,
            SecureValueRecoveryV2 secureValueRecoveryV2
    ) {
        this.keyBackupService = keyBackupService;
        this.fallbackKeyBackupServices = fallbackKeyBackupServices;
        this.secureValueRecoveryV1 = new SecureValueRecoveryV1(keyBackupService);
        this.secureValueRecoveryV2 = secureValueRecoveryV2;
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

        final var backupResponse = secureValueRecoveryV2.setPin(pin, masterKey).execute();
        if (backupResponse instanceof SecureValueRecovery.BackupResponse.Success) {
        } else if (backupResponse instanceof SecureValueRecovery.BackupResponse.ServerRejected) {
            logger.warn("Backup svr2 failed: ServerRejected");
        } else if (backupResponse instanceof SecureValueRecovery.BackupResponse.EnclaveNotFound) {
            logger.warn("Backup svr2 failed: EnclaveNotFound");
        } else if (backupResponse instanceof SecureValueRecovery.BackupResponse.ExposeFailure) {
            logger.warn("Backup svr2 failed: ExposeFailure");
        } else if (backupResponse instanceof SecureValueRecovery.BackupResponse.ApplicationError error) {
            throw new IOException(error.getException());
        } else if (backupResponse instanceof SecureValueRecovery.BackupResponse.NetworkError error) {
            throw error.getException();
        } else {
            throw new AssertionError("Unexpected response");
        }
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

        final var deleteResponse = secureValueRecoveryV2.deleteData();
        if (deleteResponse instanceof SecureValueRecovery.DeleteResponse.Success) {
        } else if (deleteResponse instanceof SecureValueRecovery.DeleteResponse.ServerRejected) {
            logger.warn("Delete svr2 failed: ServerRejected");
        } else if (deleteResponse instanceof SecureValueRecovery.DeleteResponse.EnclaveNotFound) {
            logger.warn("Delete svr2 failed: EnclaveNotFound");
        } else if (deleteResponse instanceof SecureValueRecovery.DeleteResponse.ApplicationError error) {
            throw new IOException(error.getException());
        } else if (deleteResponse instanceof SecureValueRecovery.DeleteResponse.NetworkError error) {
            throw error.getException();
        } else {
            throw new AssertionError("Unexpected response");
        }
    }

    public SecureValueRecovery.RestoreResponse.Success getRegistrationLockData(
            String pin, LockedException e
    ) throws IOException, IncorrectPinException {
        var svr1Credentials = e.getSvr1Credentials();
        if (svr1Credentials != null) {
            return getRegistrationLockData(secureValueRecoveryV1, svr1Credentials, pin);
        }

        var svr2Credentials = e.getSvr2Credentials();
        if (svr2Credentials != null) {
            return getRegistrationLockData(secureValueRecoveryV2, svr2Credentials, pin);
        }

        return null;
    }

    public SecureValueRecovery.RestoreResponse.Success getRegistrationLockData(
            SecureValueRecovery secureValueRecovery, AuthCredentials authCredentials, String pin
    ) throws IOException, IncorrectPinException {
        final var restoreResponse = secureValueRecovery.restoreDataPreRegistration(authCredentials, pin);

        if (restoreResponse instanceof SecureValueRecovery.RestoreResponse.Success s) {
            return s;
        } else if (restoreResponse instanceof SecureValueRecovery.RestoreResponse.PinMismatch pinMismatch) {
            throw new IncorrectPinException(pinMismatch.getTriesRemaining());
        } else if (restoreResponse instanceof SecureValueRecovery.RestoreResponse.ApplicationError error) {
            throw new IOException(error.getException());
        } else if (restoreResponse instanceof SecureValueRecovery.RestoreResponse.NetworkError error) {
            throw error.getException();
        } else {
            throw new AssertionError("Unexpected response");
        }
    }
}
