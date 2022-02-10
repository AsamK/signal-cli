package org.asamk.signal.manager.storage.accounts;

import java.util.List;

record AccountsStorage(List<Account> accounts) {

    record Account(String path, String number, String uuid) {}
}
