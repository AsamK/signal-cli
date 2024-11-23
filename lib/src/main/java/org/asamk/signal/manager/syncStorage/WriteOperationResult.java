package org.asamk.signal.manager.syncStorage;

import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.util.List;
import java.util.Locale;

public record WriteOperationResult(
        SignalStorageManifest manifest, List<SignalStorageRecord> inserts, List<byte[]> deletes
) {

    public boolean isEmpty() {
        return inserts.isEmpty() && deletes.isEmpty();
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "Empty";
        } else {
            return String.format(Locale.ROOT,
                    "ManifestVersion: %d, Total Keys: %d, Inserts: %d, Deletes: %d",
                    manifest.version,
                    manifest.storageIds.size(),
                    inserts.size(),
                    deletes.size());
        }
    }
}
