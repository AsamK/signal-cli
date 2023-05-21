package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.internal.JobExecutor;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.AttachmentStore;
import org.asamk.signal.manager.storage.AvatarStore;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.stickerPacks.StickerPackStore;

import java.util.function.Supplier;

public class Context {

    private final Object LOCK = new Object();

    private final SignalAccount account;
    private final AccountFileUpdater accountFileUpdater;
    private final SignalDependencies dependencies;
    private final AvatarStore avatarStore;
    private final StickerPackStore stickerPackStore;
    private final AttachmentStore attachmentStore;
    private final JobExecutor jobExecutor;

    private AccountHelper accountHelper;
    private AttachmentHelper attachmentHelper;
    private ContactHelper contactHelper;
    private GroupHelper groupHelper;
    private GroupV2Helper groupV2Helper;
    private IdentityHelper identityHelper;
    private IncomingMessageHandler incomingMessageHandler;
    private PinHelper pinHelper;
    private PreKeyHelper preKeyHelper;
    private ProfileHelper profileHelper;
    private ReceiveHelper receiveHelper;
    private RecipientHelper recipientHelper;
    private SendHelper sendHelper;
    private StickerHelper stickerHelper;
    private StorageHelper storageHelper;
    private SyncHelper syncHelper;
    private UnidentifiedAccessHelper unidentifiedAccessHelper;

    public Context(
            final SignalAccount account,
            final AccountFileUpdater accountFileUpdater,
            final SignalDependencies dependencies,
            final AvatarStore avatarStore,
            final AttachmentStore attachmentStore,
            final StickerPackStore stickerPackStore
    ) {
        this.account = account;
        this.accountFileUpdater = accountFileUpdater;
        this.dependencies = dependencies;
        this.avatarStore = avatarStore;
        this.stickerPackStore = stickerPackStore;
        this.attachmentStore = attachmentStore;
        this.jobExecutor = new JobExecutor(this);
    }

    public SignalAccount getAccount() {
        return account;
    }

    public AccountFileUpdater getAccountFileUpdater() {
        return accountFileUpdater;
    }

    public SignalDependencies getDependencies() {
        return dependencies;
    }

    public AvatarStore getAvatarStore() {
        return avatarStore;
    }

    public StickerPackStore getStickerPackStore() {
        return stickerPackStore;
    }

    AttachmentStore getAttachmentStore() {
        return attachmentStore;
    }

    JobExecutor getJobExecutor() {
        return jobExecutor;
    }

    public AccountHelper getAccountHelper() {
        return getOrCreate(() -> accountHelper, () -> accountHelper = new AccountHelper(this));
    }

    public AttachmentHelper getAttachmentHelper() {
        return getOrCreate(() -> attachmentHelper, () -> attachmentHelper = new AttachmentHelper(this));
    }

    public ContactHelper getContactHelper() {
        return getOrCreate(() -> contactHelper, () -> contactHelper = new ContactHelper(account));
    }

    GroupV2Helper getGroupV2Helper() {
        return getOrCreate(() -> groupV2Helper, () -> groupV2Helper = new GroupV2Helper(this));
    }

    public GroupHelper getGroupHelper() {
        return getOrCreate(() -> groupHelper, () -> groupHelper = new GroupHelper(this));
    }

    public IdentityHelper getIdentityHelper() {
        return getOrCreate(() -> identityHelper, () -> identityHelper = new IdentityHelper(this));
    }

    public IncomingMessageHandler getIncomingMessageHandler() {
        return getOrCreate(() -> incomingMessageHandler,
                () -> this.incomingMessageHandler = new IncomingMessageHandler(this));
    }

    PinHelper getPinHelper() {
        return getOrCreate(() -> pinHelper,
                () -> pinHelper = new PinHelper(dependencies.getKeyBackupService(),
                        dependencies.getFallbackKeyBackupServices()));
    }

    public PreKeyHelper getPreKeyHelper() {
        return getOrCreate(() -> preKeyHelper, () -> preKeyHelper = new PreKeyHelper(account, dependencies));
    }

    public ProfileHelper getProfileHelper() {
        return getOrCreate(() -> profileHelper, () -> profileHelper = new ProfileHelper(this));
    }

    public ReceiveHelper getReceiveHelper() {
        return getOrCreate(() -> receiveHelper, () -> receiveHelper = new ReceiveHelper(this));
    }

    public RecipientHelper getRecipientHelper() {
        return getOrCreate(() -> recipientHelper, () -> recipientHelper = new RecipientHelper(this));
    }

    public SendHelper getSendHelper() {
        return getOrCreate(() -> sendHelper, () -> sendHelper = new SendHelper(this));
    }

    public StickerHelper getStickerHelper() {
        return getOrCreate(() -> stickerHelper, () -> stickerHelper = new StickerHelper(this));
    }

    public StorageHelper getStorageHelper() {
        return getOrCreate(() -> storageHelper, () -> storageHelper = new StorageHelper(this));
    }

    public SyncHelper getSyncHelper() {
        return getOrCreate(() -> syncHelper, () -> syncHelper = new SyncHelper(this));
    }

    UnidentifiedAccessHelper getUnidentifiedAccessHelper() {
        return getOrCreate(() -> unidentifiedAccessHelper,
                () -> unidentifiedAccessHelper = new UnidentifiedAccessHelper(this));
    }

    private <T> T getOrCreate(Supplier<T> supplier, Callable creator) {
        var value = supplier.get();
        if (value != null) {
            return value;
        }

        synchronized (LOCK) {
            value = supplier.get();
            if (value != null) {
                return value;
            }
            creator.call();
            return supplier.get();
        }
    }

    private interface Callable {

        void call();
    }
}
