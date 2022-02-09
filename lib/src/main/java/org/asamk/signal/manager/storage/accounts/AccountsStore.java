package org.asamk.signal.manager.storage.accounts;

import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class AccountsStore {

    private final File dataPath;

    public AccountsStore(final File dataPath) {
        this.dataPath = dataPath;
    }

    public Set<String> getAllNumbers() {
        final var files = dataPath.listFiles();

        if (files == null) {
            return Set.of();
        }

        return Arrays.stream(files)
                .filter(File::isFile)
                .map(File::getName)
                .filter(file -> PhoneNumberFormatter.isValidNumber(file, null))
                .collect(Collectors.toSet());
    }

    public String getPathByNumber(String number) {
        return number;
    }

    public String getPathByAci(ACI aci) {
        return null;
    }

    public void updateAccount(String path, String number, ACI aci) {
        // TODO remove number and uuid from all other accounts
        if (!path.equals(number)) {
            throw new UnsupportedOperationException("Updating number not supported yet");
        }
    }

    public String addAccount(String number, ACI aci) {
        // TODO remove number and uuid from all other accounts
        return number;
    }
}
