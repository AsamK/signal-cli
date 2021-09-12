package org.asamk.signal.manager.jobs;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.StickerPackStore;
import org.asamk.signal.manager.helper.GroupHelper;
import org.asamk.signal.manager.helper.PreKeyHelper;
import org.asamk.signal.manager.helper.ProfileHelper;
import org.asamk.signal.manager.helper.SendHelper;
import org.asamk.signal.manager.helper.StorageHelper;
import org.asamk.signal.manager.helper.SyncHelper;
import org.asamk.signal.manager.storage.SignalAccount;

public class Context {

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final StickerPackStore stickerPackStore;
    private final SendHelper sendHelper;
    private final GroupHelper groupHelper;
    private final SyncHelper syncHelper;
    private final ProfileHelper profileHelper;
    private final StorageHelper storageHelper;
    private final PreKeyHelper preKeyHelper;

    public Context(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final StickerPackStore stickerPackStore,
            final SendHelper sendHelper,
            final GroupHelper groupHelper,
            final SyncHelper syncHelper,
            final ProfileHelper profileHelper,
            final StorageHelper storageHelper,
            final PreKeyHelper preKeyHelper
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.stickerPackStore = stickerPackStore;
        this.sendHelper = sendHelper;
        this.groupHelper = groupHelper;
        this.syncHelper = syncHelper;
        this.profileHelper = profileHelper;
        this.storageHelper = storageHelper;
        this.preKeyHelper = preKeyHelper;
    }

    public SignalAccount getAccount() {
        return account;
    }

    public SignalDependencies getDependencies() {
        return dependencies;
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

    public StorageHelper getStorageHelper() {
        return storageHelper;
    }

    public PreKeyHelper getPreKeyHelper() {
        return preKeyHelper;
    }
}
