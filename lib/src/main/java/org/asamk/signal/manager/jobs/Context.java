package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.StickerPackStore;
import org.asamk.signal.manager.helper.GroupHelper;
import org.asamk.signal.manager.helper.ProfileHelper;
import org.asamk.signal.manager.helper.SendHelper;
import org.asamk.signal.manager.helper.SyncHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

public class Context {

    private final SignalAccount account;
    private final SignalServiceAccountManager accountManager;
    private final SignalServiceMessageReceiver messageReceiver;
    private final StickerPackStore stickerPackStore;
    private final SendHelper sendHelper;
    private final GroupHelper groupHelper;
    private final SyncHelper syncHelper;
    private final ProfileHelper profileHelper;

    public Context(
            final SignalAccount account,
            final SignalServiceAccountManager accountManager,
            final SignalServiceMessageReceiver messageReceiver,
            final StickerPackStore stickerPackStore,
            final SendHelper sendHelper,
            final GroupHelper groupHelper,
            final SyncHelper syncHelper,
            final ProfileHelper profileHelper
    ) {
        this.account = account;
        this.accountManager = accountManager;
        this.messageReceiver = messageReceiver;
        this.stickerPackStore = stickerPackStore;
        this.sendHelper = sendHelper;
        this.groupHelper = groupHelper;
        this.syncHelper = syncHelper;
        this.profileHelper = profileHelper;
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

    public SendHelper getSendHelper() {
        return sendHelper;
    }

    public GroupHelper getGroupHelper() {
        return groupHelper;
    }

    public SyncHelper getSyncHelper() {
        return syncHelper;
    }

    public ProfileHelper getProfileHelper() {
        return profileHelper;
    }
}
