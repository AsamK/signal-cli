package org.asamk.signal.manager.storage.accounts;

import java.util.List;

public record AccountsStorage(List<Account> accounts) {

    public record Account(String path, String number, String uuid) {}
}
