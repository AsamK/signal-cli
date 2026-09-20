package org.asamk.signal.manager.storage.accounts;

import java.util.List;

public record AccountsStorage(List<Account> accounts, Integer version) {

    public record Account(String path, String environment, String number, String uuid) {}
}
