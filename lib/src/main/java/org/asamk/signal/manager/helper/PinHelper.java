package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.IncorrectPinException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.util.List;

public class PinHelper {

    private static final Logger logger = LoggerFactory.getLogger(PinHelper.class);

    private final List<SecureValueRecovery> secureValueRecoveries;

    public PinHelper(final List<SecureValueRecovery> secureValueRecoveries) {
        this.secureValueRecoveries = secureValueRecoveries;
    }

    public void setRegistrationLockPin(String pin, MasterKey masterKey) throws IOException {
        IOException exception = null;
        for (final var secureValueRecovery : secureValueRecoveries) {
            try {
                final var backupResponse = secureValueRecovery.setPin(pin, masterKey).execute();
                switch (backupResponse) {
                    case SecureValueRecovery.BackupResponse.Success success -> {
                    }
                    case SecureValueRecovery.BackupResponse.ServerRejected serverRejected ->
                            logger.warn("Backup svr failed: ServerRejected");
                    case SecureValueRecovery.BackupResponse.EnclaveNotFound enclaveNotFound ->
                            logger.warn("Backup svr failed: EnclaveNotFound");
                    case SecureValueRecovery.BackupResponse.ExposeFailure exposeFailure ->
                            logger.warn("Backup svr failed: ExposeFailure");
                    case SecureValueRecovery.BackupResponse.ApplicationError error ->
                            throw new IOException(error.getException());
                    case SecureValueRecovery.BackupResponse.NetworkError error -> throw error.getException();
                    case null, default -> throw new AssertionError("Unexpected response");
                }
            } catch (IOException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    public void migrateRegistrationLockPin(String pin, MasterKey masterKey) throws IOException {
        setRegistrationLockPin(pin, masterKey);
    }

    public void removeRegistrationLockPin() throws IOException {
        IOException exception = null;
        for (final var secureValueRecovery : secureValueRecoveries) {
            try {
                final var deleteResponse = secureValueRecovery.deleteData();
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
            } catch (IOException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    public SecureValueRecovery.RestoreResponse.Success getRegistrationLockData(
            String pin,
            LockedException lockedException
    ) throws IOException, IncorrectPinException {
        var svr2Credentials = lockedException.getSvr2Credentials();
        if (svr2Credentials != null) {
            IOException exception = null;
            for (final var secureValueRecovery : secureValueRecoveries) {
                try {
                    final var lockData = getRegistrationLockData(secureValueRecovery, svr2Credentials, pin);
                    if (lockData == null) {
                        continue;
                    }
                    return lockData;
                } catch (IOException e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }

        return null;
    }

    public SecureValueRecovery.RestoreResponse.Success getRegistrationLockData(
            SecureValueRecovery secureValueRecovery,
            AuthCredentials authCredentials,
            String pin
    ) throws IOException, IncorrectPinException {
        final var restoreResponse = secureValueRecovery.restoreDataPreRegistration(authCredentials, null, pin);

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
