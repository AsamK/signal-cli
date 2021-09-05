package org.asamk.signal.manager.storage.protocol;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class SignalProtocolStore implements SignalServiceDataStore {

    private final PreKeyStore preKeyStore;
    private final SignedPreKeyStore signedPreKeyStore;
    private final SignalServiceSessionStore sessionStore;
    private final IdentityKeyStore identityKeyStore;
    private final SignalServiceSenderKeyStore senderKeyStore;
    private final Supplier<Boolean> isMultiDevice;

    public SignalProtocolStore(
            final PreKeyStore preKeyStore,
            final SignedPreKeyStore signedPreKeyStore,
            final SignalServiceSessionStore sessionStore,
            final IdentityKeyStore identityKeyStore,
            final SignalServiceSenderKeyStore senderKeyStore,
            final Supplier<Boolean> isMultiDevice
    ) {
        this.preKeyStore = preKeyStore;
        this.signedPreKeyStore = signedPreKeyStore;
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
    public Transaction beginTransaction() {
        return () -> {
            // No-op transaction should be safe, as it's only a performance improvement
        };
    }
}
