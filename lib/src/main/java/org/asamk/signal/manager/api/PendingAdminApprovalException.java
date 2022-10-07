package org.asamk.signal.manager.api;

public class PendingAdminApprovalException extends Exception {

    public PendingAdminApprovalException(final String message) {
        super(message);
    }

    public PendingAdminApprovalException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
