package org.asamk.signal.manager.storage.accounts;

import java.util.List;

public record AccountsStorage(List<Account> accounts, Integer version) {

    // Older indexes omit this flag; entries with only a UUID must then be checked against their account state.
    public record Account(String path, String environment, String number, String uuid, Boolean numberless) {}
}
