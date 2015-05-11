package cli;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JsonIdentityKeyStore implements IdentityKeyStore {

    private final Map<String, IdentityKey> trustedKeys = new HashMap<>();

    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;

    public JsonIdentityKeyStore(JSONObject jsonAxolotl) throws IOException, InvalidKeyException {
        localRegistrationId = jsonAxolotl.getInt("registrationId");
        identityKeyPair = new IdentityKeyPair(Base64.decode(jsonAxolotl.getString("identityKey")));

        JSONArray list = jsonAxolotl.getJSONArray("trustedKeys");
        for (int i = 0; i < list.length(); i++) {
            JSONObject k = list.getJSONObject(i);
            try {
                trustedKeys.put(k.getString("name"), new IdentityKey(Base64.decode(k.getString("identityKey")), 0));
            } catch (InvalidKeyException | IOException e) {
                System.out.println("Error while decoding key for: " + k.getString("name"));
            }
        }
    }

    public JsonIdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    public JSONObject getJson() {
        JSONArray list = new JSONArray();
        for (String name : trustedKeys.keySet()) {
            list.put(new JSONObject().put("name", name).put("identityKey", Base64.encodeBytes(trustedKeys.get(name).serialize())));
        }

        JSONObject result = new JSONObject();
        result.put("registrationId", localRegistrationId);
        result.put("identityKey", Base64.encodeBytes(identityKeyPair.serialize()));
        result.put("trustedKeys", list);
        return result;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    @Override
    public void saveIdentity(String name, IdentityKey identityKey) {
        trustedKeys.put(name, identityKey);
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        IdentityKey trusted = trustedKeys.get(name);
        return (trusted == null || trusted.equals(identityKey));
    }
}
