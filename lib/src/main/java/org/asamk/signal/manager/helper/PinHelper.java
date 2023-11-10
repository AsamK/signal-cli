package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;

public class PinHelper {

    private static final Logger logger = LoggerFactory.getLogger(PinHelper.class);

    private final SecureValueRecoveryV2 secureValueRecoveryV2;

    public PinHelper(final SecureValueRecoveryV2 secureValueRecoveryV2) {
        this.secureValueRecoveryV2 = secureValueRecoveryV2;
    }

    public void setRegistrationLockPin(
            String pin, MasterKey masterKey
    ) throws IOException {
        final var backupResponse = secureValueRecoveryV2.setPin(pin, masterKey).execute();
        switch (backupResponse) {
            case SecureValueRecovery.BackupResponse.Success success -> {
            }
            case SecureValueRecovery.BackupResponse.ServerRejected serverRejected ->
                    logger.warn("Backup svr2 failed: ServerRejected");
            case SecureValueRecovery.BackupResponse.EnclaveNotFound enclaveNotFound ->
                    logger.warn("Backup svr2 failed: EnclaveNotFound");
            case SecureValueRecovery.BackupResponse.ExposeFailure exposeFailure ->
                    logger.warn("Backup svr2 failed: ExposeFailure");
            case SecureValueRecovery.BackupResponse.ApplicationError error ->
                    throw new IOException(error.getException());
            case SecureValueRecovery.BackupResponse.NetworkError error -> throw error.getException();
            case null, default -> throw new AssertionError("Unexpected response");
        }
    }

    public void migrateRegistrationLockPin(String pin, MasterKey masterKey) throws IOException {
        setRegistrationLockPin(pin, masterKey);
    }

    public void removeRegistrationLockPin() throws IOException {
        final var deleteResponse = secureValueRecoveryV2.deleteData();
        switch (deleteResponse) {
            case SecureValueRecovery.DeleteResponse.Success success -> {
            }
            case SecureValueRecovery.DeleteResponse.ServerRejected serverRejected ->
                    logger.warn("Delete svr2 failed: ServerRejected");
            case SecureValueRecovery.DeleteResponse.EnclaveNotFound enclaveNotFound ->
                    logger.warn("Delete svr2 failed: EnclaveNotFound");
            case SecureValueRecovery.DeleteResponse.ApplicationError error ->
                    throw new IOException(error.getException());
            case SecureValueRecovery.DeleteResponse.NetworkError error -> throw error.getException();
            case null, default -> throw new AssertionError("Unexpected response");
        }
    }

    public SecureValueRecovery.RestoreResponse.Success getRegistrationLockData(
            String pin, LockedException e
    ) throws IOException, IncorrectPinException {
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

        switch (restoreResponse) {
            case SecureValueRecovery.RestoreResponse.Success s -> {
                return s;
            }
            case SecureValueRecovery.RestoreResponse.PinMismatch pinMismatch ->
                    throw new IncorrectPinException(pinMismatch.getTriesRemaining());
            case SecureValueRecovery.RestoreResponse.ApplicationError error ->
                    throw new IOException(error.getException());
            case SecureValueRecovery.RestoreResponse.NetworkError error -> throw error.getException();
            case SecureValueRecovery.RestoreResponse.Missing missing -> {
                logger.debug("No SVR data stored for the given credentials.");
                return null;
            }
            case null, default ->
                    throw new AssertionError("Unexpected response: " + restoreResponse.getClass().getSimpleName());
        }
    }
}
