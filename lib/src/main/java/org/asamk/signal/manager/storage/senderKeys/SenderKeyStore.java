package org.asamk.signal.manager.storage.senderKeys;

import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.storage.Database;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SenderKeyStore implements SignalServiceSenderKeyStore {

    private final SenderKeyRecordStore senderKeyRecordStore;
    private final SenderKeySharedStore senderKeySharedStore;

    public SenderKeyStore(final Database database) {
        this.senderKeyRecordStore = new SenderKeyRecordStore(database);
        this.senderKeySharedStore = new SenderKeySharedStore(database);
    }

    @Override
    public void storeSenderKey(
            final SignalProtocolAddress sender, final UUID distributionId, final SenderKeyRecord record
    ) {
        senderKeyRecordStore.storeSenderKey(sender, distributionId, record);
    }

    @Override
    public SenderKeyRecord loadSenderKey(final SignalProtocolAddress sender, final UUID distributionId) {
        return senderKeyRecordStore.loadSenderKey(sender, distributionId);
    }

    @Override
    public Set<SignalProtocolAddress> getSenderKeySharedWith(final DistributionId distributionId) {
        return senderKeySharedStore.getSenderKeySharedWith(distributionId);
    }

    @Override
    public void markSenderKeySharedWith(
            final DistributionId distributionId, final Collection<SignalProtocolAddress> addresses
    ) {
        senderKeySharedStore.markSenderKeySharedWith(distributionId, addresses);
    }

    @Override
    public void clearSenderKeySharedWith(final Collection<SignalProtocolAddress> addresses) {
        senderKeySharedStore.clearSenderKeySharedWith(addresses);
    }

    public void deleteAll() {
        senderKeySharedStore.deleteAll();
        senderKeyRecordStore.deleteAll();
    }

    public void deleteAll(ServiceId serviceId) {
        senderKeySharedStore.deleteAllFor(serviceId);
        senderKeyRecordStore.deleteAllFor(serviceId);
    }

    public void deleteSharedWith(ServiceId serviceId) {
        senderKeySharedStore.deleteAllFor(serviceId);
    }

    public void deleteSharedWith(ServiceId serviceId, int deviceId, DistributionId distributionId) {
        senderKeySharedStore.deleteSharedWith(serviceId, deviceId, distributionId);
    }

    public void deleteOurKey(ServiceId selfServiceId, DistributionId distributionId) {
        senderKeySharedStore.deleteAllFor(distributionId);
        senderKeyRecordStore.deleteSenderKey(selfServiceId, distributionId.asUuid());
    }

    public long getCreateTimeForOurKey(ServiceId selfServiceId, int deviceId, DistributionId distributionId) {
        return senderKeyRecordStore.getCreateTimeForKey(selfServiceId, deviceId, distributionId.asUuid());
    }

    void addLegacySenderKeys(final Collection<Pair<SenderKeyRecordStore.Key, SenderKeyRecord>> senderKeys) {
        senderKeyRecordStore.addLegacySenderKeys(senderKeys);
    }

    void addLegacySenderKeysShared(final Map<DistributionId, Set<SenderKeySharedStore.SenderKeySharedEntry>> sharedSenderKeys) {
        senderKeySharedStore.addLegacySenderKeysShared(sharedSenderKeys);
    }
}
