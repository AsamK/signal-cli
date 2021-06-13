package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.StickerPackStore;
import org.asamk.signal.manager.storage.SignalAccount;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

public class Context {

    private SignalAccount account;
    private SignalServiceAccountManager accountManager;
    private SignalServiceMessageReceiver messageReceiver;
    private StickerPackStore stickerPackStore;

    public Context(
            final SignalAccount account,
            final SignalServiceAccountManager accountManager,
            final SignalServiceMessageReceiver messageReceiver,
            final StickerPackStore stickerPackStore
    ) {
        this.account = account;
        this.accountManager = accountManager;
        this.messageReceiver = messageReceiver;
        this.stickerPackStore = stickerPackStore;
    }

    public SignalAccount getAccount() {
        return account;
    }

    public SignalServiceAccountManager getAccountManager() {
        return accountManager;
    }

    public SignalServiceMessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public StickerPackStore getStickerPackStore() {
        return stickerPackStore;
    }
}
