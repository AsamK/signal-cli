package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV1;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.util.Collection;

public class PinHelper {

    private final static Logger logger = LoggerFactory.getLogger(PinHelper.class);

    private final SecureValueRecoveryV1 secureValueRecoveryV1;
    private final SecureValueRecoveryV2 secureValueRecoveryV2;
    private final Collection<KeyBackupService> fallbackKeyBackupServices;

    public PinHelper(
            final SecureValueRecoveryV1 secureValueRecoveryV1,
            final SecureValueRecoveryV2 secureValueRecoveryV2,
            final Collection<KeyBackupService> fallbackKeyBackupServices
    ) {
        this.fallbackKeyBackupServices = fallbackKeyBackupServices;
        this.secureValueRecoveryV1 = secureValueRecoveryV1;
        this.secureValueRecoveryV2 = secureValueRecoveryV2;
    }

    public void setRegistrationLockPin(
            String pin, MasterKey masterKey
    ) throws IOException {
        secureValueRecoveryV1.setPin(pin, masterKey).execute();
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
        secureValueRecoveryV1.deleteData();
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
            final var registrationLockData = getRegistrationLockData(secureValueRecoveryV1, svr1Credentials, pin);
            if (registrationLockData != null) {
                return registrationLockData;
            }
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
        } else if (restoreResponse instanceof SecureValueRecovery.RestoreResponse.Missing) {
            logger.debug("No SVR data stored for the given credentials.");
            return null;
        } else {
            throw new AssertionError("Unexpected response: " + restoreResponse.getClass().getSimpleName());
        }
    }
}
