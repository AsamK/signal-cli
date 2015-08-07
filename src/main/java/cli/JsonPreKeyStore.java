package cli;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class JsonPreKeyStore implements PreKeyStore {

    private final Map<Integer, byte[]> store = new HashMap<>();

    public JsonPreKeyStore() {

    }

    public JsonPreKeyStore(JSONArray list) {
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
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        try {
            if (!store.containsKey(preKeyId)) {
                throw new InvalidKeyIdException("No such prekeyrecord!");
            }

            return new PreKeyRecord(store.get(preKeyId));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        store.put(preKeyId, record.serialize());
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return store.containsKey(preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        store.remove(preKeyId);
    }
}
