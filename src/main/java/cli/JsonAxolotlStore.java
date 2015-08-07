package cli;

import org.json.JSONObject;
import org.whispersystems.libaxolotl.*;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.io.IOException;
import java.util.List;

class JsonAxolotlStore implements AxolotlStore {
    private final JsonPreKeyStore preKeyStore;
    private final JsonSessionStore sessionStore;
    private final JsonSignedPreKeyStore signedPreKeyStore;

    private final JsonIdentityKeyStore identityKeyStore;

    public JsonAxolotlStore(JSONObject jsonAxolotl) throws IOException, InvalidKeyException {
        this.preKeyStore = new JsonPreKeyStore(jsonAxolotl.getJSONArray("preKeys"));
        this.sessionStore = new JsonSessionStore(jsonAxolotl.getJSONArray("sessionStore"));
        this.signedPreKeyStore = new JsonSignedPreKeyStore(jsonAxolotl.getJSONArray("signedPreKeyStore"));
        this.identityKeyStore = new JsonIdentityKeyStore(jsonAxolotl.getJSONObject("identityKeyStore"));
    }

    public JsonAxolotlStore(IdentityKeyPair identityKeyPair, int registrationId) {
        preKeyStore = new JsonPreKeyStore();
        sessionStore = new JsonSessionStore();
        signedPreKeyStore = new JsonSignedPreKeyStore();
        this.identityKeyStore = new JsonIdentityKeyStore(identityKeyPair, registrationId);
    }

    public JSONObject getJson() {
        return new JSONObject().put("preKeys", preKeyStore.getJson())
                .put("sessionStore", sessionStore.getJson())
                .put("signedPreKeyStore", signedPreKeyStore.getJson())
                .put("identityKeyStore", identityKeyStore.getJson());
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
    public void saveIdentity(String name, IdentityKey identityKey) {
        identityKeyStore.saveIdentity(name, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        return identityKeyStore.isTrustedIdentity(name, identityKey);
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
    public SessionRecord loadSession(AxolotlAddress address) {
        return sessionStore.loadSession(address);
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return sessionStore.getSubDeviceSessions(name);
    }

    @Override
    public void storeSession(AxolotlAddress address, SessionRecord record) {
        sessionStore.storeSession(address, record);
    }

    @Override
    public boolean containsSession(AxolotlAddress address) {
        return sessionStore.containsSession(address);
    }

    @Override
    public void deleteSession(AxolotlAddress address) {
        sessionStore.deleteSession(address);
    }

    @Override
    public void deleteAllSessions(String name) {
        sessionStore.deleteAllSessions(name);
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
}
