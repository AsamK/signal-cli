package org.asamk.signal.manager.storage.protocol;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.state.IdentityKeyStore;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceKyberPreKeyStore;
import org.whispersystems.signalservice.api.SignalServicePreKeyStore;
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class SignalProtocolStore implements SignalServiceAccountDataStore {

    private final SignalServicePreKeyStore preKeyStore;
    private final SignedPreKeyStore signedPreKeyStore;
    private final SignalServiceKyberPreKeyStore kyberPreKeyStore;
    private final SignalServiceSessionStore sessionStore;
    private final IdentityKeyStore identityKeyStore;
    private final SignalServiceSenderKeyStore senderKeyStore;
    private final Supplier<Boolean> isMultiDevice;

    public SignalProtocolStore(
            final SignalServicePreKeyStore preKeyStore,
            final SignedPreKeyStore signedPreKeyStore,
            final SignalServiceKyberPreKeyStore kyberPreKeyStore,
            final SignalServiceSessionStore sessionStore,
            final IdentityKeyStore identityKeyStore,
            final SignalServiceSenderKeyStore senderKeyStore,
            final Supplier<Boolean> isMultiDevice
    ) {
        this.preKeyStore = preKeyStore;
        this.signedPreKeyStore = signedPreKeyStore;
        this.kyberPreKeyStore = kyberPreKeyStore;
        this.sessionStore = sessionStore;
        this.identityKeyStore = identityKeyStore;
        this.senderKeyStore = senderKeyStore;
        this.isMultiDevice = isMultiDevice;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyStore.getIdentityKeyPair();
    }

    @Override
    public int getLocalRegistrationId() {
        return identityKeyStore.getLocalRegistrationId();
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        return identityKeyStore.saveIdentity(address, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        return identityKeyStore.isTrustedIdentity(address, identityKey, direction);
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        return identityKeyStore.getIdentity(address);
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        return preKeyStore.loadPreKey(preKeyId);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        preKeyStore.storePreKey(preKeyId, record);
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return preKeyStore.containsPreKey(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        preKeyStore.removePreKey(preKeyId);
    }

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return sessionStore.loadSession(address);
    }

    @Override
    public List<SessionRecord> loadExistingSessions(final List<SignalProtocolAddress> addresses) throws NoSessionException {
        return sessionStore.loadExistingSessions(addresses);
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return sessionStore.getSubDeviceSessions(name);
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessionStore.storeSession(address, record);
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return sessionStore.containsSession(address);
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        sessionStore.deleteSession(address);
    }

    @Override
    public void deleteAllSessions(String name) {
        sessionStore.deleteAllSessions(name);
    }

    @Override
    public void archiveSession(final SignalProtocolAddress address) {
        sessionStore.archiveSession(address);
        senderKeyStore.clearSenderKeySharedWith(List.of(address));
    }

    @Override
    public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(final List<String> addressNames) {
        return sessionStore.getAllAddressesWithActiveSessions(addressNames);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        return signedPreKeyStore.loadSignedPreKey(signedPreKeyId);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return signedPreKeyStore.loadSignedPreKeys();
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return signedPreKeyStore.containsSignedPreKey(signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        signedPreKeyStore.removeSignedPreKey(signedPreKeyId);
    }

    @Override
    public void storeSenderKey(
            final SignalProtocolAddress sender, final UUID distributionId, final SenderKeyRecord record
    ) {
        senderKeyStore.storeSenderKey(sender, distributionId, record);
    }

    @Override
    public SenderKeyRecord loadSenderKey(final SignalProtocolAddress sender, final UUID distributionId) {
        return senderKeyStore.loadSenderKey(sender, distributionId);
    }

    @Override
    public Set<SignalProtocolAddress> getSenderKeySharedWith(final DistributionId distributionId) {
        return senderKeyStore.getSenderKeySharedWith(distributionId);
    }

    @Override
    public void markSenderKeySharedWith(
            final DistributionId distributionId, final Collection<SignalProtocolAddress> addresses
    ) {
        senderKeyStore.markSenderKeySharedWith(distributionId, addresses);
    }

    @Override
    public void clearSenderKeySharedWith(final Collection<SignalProtocolAddress> addresses) {
        senderKeyStore.clearSenderKeySharedWith(addresses);
    }

    @Override
    public boolean isMultiDevice() {
        return isMultiDevice.get();
    }

    @Override
    public KyberPreKeyRecord loadKyberPreKey(final int kyberPreKeyId) throws InvalidKeyIdException {
        return kyberPreKeyStore.loadKyberPreKey(kyberPreKeyId);
    }

    @Override
    public List<KyberPreKeyRecord> loadKyberPreKeys() {
        return kyberPreKeyStore.loadKyberPreKeys();
    }

    @Override
    public void storeKyberPreKey(final int kyberPreKeyId, final KyberPreKeyRecord record) {
        kyberPreKeyStore.storeKyberPreKey(kyberPreKeyId, record);
    }

    @Override
    public boolean containsKyberPreKey(final int kyberPreKeyId) {
        return kyberPreKeyStore.containsKyberPreKey(kyberPreKeyId);
    }

    @Override
    public void markKyberPreKeyUsed(final int kyberPreKeyId) {
        kyberPreKeyStore.markKyberPreKeyUsed(kyberPreKeyId);
    }

    @Override
    public List<KyberPreKeyRecord> loadLastResortKyberPreKeys() {
        return kyberPreKeyStore.loadLastResortKyberPreKeys();
    }

    @Override
    public void removeKyberPreKey(final int i) {
        kyberPreKeyStore.removeKyberPreKey(i);
    }

    @Override
    public void storeLastResortKyberPreKey(final int i, final KyberPreKeyRecord kyberPreKeyRecord) {
        kyberPreKeyStore.storeLastResortKyberPreKey(i, kyberPreKeyRecord);
    }

    @Override
    public void deleteAllStaleOneTimeKyberPreKeys(final long threshold, final int minCount) {
        kyberPreKeyStore.deleteAllStaleOneTimeKyberPreKeys(threshold, minCount);
    }

    @Override
    public void markAllOneTimeKyberPreKeysStaleIfNecessary(final long staleTime) {
        kyberPreKeyStore.markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime);
    }

    @Override
    public void deleteAllStaleOneTimeEcPreKeys(final long threshold, final int minCount) {
        preKeyStore.deleteAllStaleOneTimeEcPreKeys(threshold, minCount);
    }

    @Override
    public void markAllOneTimeEcPreKeysStaleIfNecessary(final long staleTime) {
        preKeyStore.markAllOneTimeEcPreKeysStaleIfNecessary(staleTime);
    }
}
