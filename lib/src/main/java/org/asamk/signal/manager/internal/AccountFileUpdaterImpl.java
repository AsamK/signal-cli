package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.storage.accounts.AccountsStore;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

public class AccountFileUpdaterImpl implements AccountFileUpdater {

    private final AccountsStore accountsStore;
    private final String accountPath;

    public AccountFileUpdaterImpl(final AccountsStore accountsStore, final String accountPath) {
        this.accountsStore = accountsStore;
        this.accountPath = accountPath;
    }

    @Override
    public void updateAccountIdentifiers(final String newNumber, final ACI newAci) {
        accountsStore.updateAccount(accountPath, newNumber, newAci);
    }

    @Override
    public void removeAccount() {
        accountsStore.removeAccount(accountPath);
    }
}
