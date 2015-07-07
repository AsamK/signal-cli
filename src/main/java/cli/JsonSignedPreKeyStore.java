package cli;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JsonSignedPreKeyStore implements SignedPreKeyStore {

    private final Map<Integer, byte[]> store = new HashMap<>();

    public JsonSignedPreKeyStore() {

    }

    public JsonSignedPreKeyStore(JSONArray list) throws IOException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject k = list.getJSONObject(i);
            try {
                store.put(k.getInt("id"), Base64.decode(k.getString("record")));
            } catch (IOException e) {
                System.out.println("Error while decoding prekey for: " + k.getString("name"));
            }
        }
    }

    public JSONArray getJson() {
        JSONArray result = new JSONArray();
        for (Integer id : store.keySet()) {
            result.put(new JSONObject().put("id", id.toString()).put("record", Base64.encodeBytes(store.get(id))));
        }
        return result;
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        try {
            if (!store.containsKey(signedPreKeyId)) {
                throw new InvalidKeyIdException("No such signedprekeyrecord! " + signedPreKeyId);
            }

            return new SignedPreKeyRecord(store.get(signedPreKeyId));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        try {
            List<SignedPreKeyRecord> results = new LinkedList<>();

            for (byte[] serialized : store.values()) {
                results.add(new SignedPreKeyRecord(serialized));
            }

            return results;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        store.put(signedPreKeyId, record.serialize());
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return store.containsKey(signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        store.remove(signedPreKeyId);
    }
}
