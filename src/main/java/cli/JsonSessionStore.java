package cli;

import org.json.JSONArray;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JsonSessionStore implements SessionStore {

    private Map<AxolotlAddress, byte[]> sessions = new HashMap<>();

    public JsonSessionStore() {

    }

    public JsonSessionStore(JSONArray list) throws IOException {
        for (int i = 0; i < list.length(); i++) {
            JSONObject k = list.getJSONObject(i);
            try {
                sessions.put(new AxolotlAddress(k.getString("name"), k.getInt("deviceId")), Base64.decode(k.getString("record")));
            } catch (IOException e) {
                System.out.println("Error while decoding prekey for: " + k.getString("name"));
            }
        }
    }

    public JSONArray getJson() {
        JSONArray result = new JSONArray();
        for (AxolotlAddress address : sessions.keySet()) {
            result.put(new JSONObject().put("name", address.getName()).
                    put("deviceId", address.getDeviceId()).
                    put("record", Base64.encodeBytes(sessions.get(address))));
        }
        return result;
    }

    @Override
    public synchronized SessionRecord loadSession(AxolotlAddress remoteAddress) {
        try {
            if (containsSession(remoteAddress)) {
                return new SessionRecord(sessions.get(remoteAddress));
            } else {
                return new SessionRecord();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public synchronized List<Integer> getSubDeviceSessions(String name) {
        List<Integer> deviceIds = new LinkedList<>();

        for (AxolotlAddress key : sessions.keySet()) {
            if (key.getName().equals(name) &&
                    key.getDeviceId() != 1) {
                deviceIds.add(key.getDeviceId());
            }
        }

        return deviceIds;
    }

    @Override
    public synchronized void storeSession(AxolotlAddress address, SessionRecord record) {
        sessions.put(address, record.serialize());
    }

    @Override
    public synchronized boolean containsSession(AxolotlAddress address) {
        return sessions.containsKey(address);
    }

    @Override
    public synchronized void deleteSession(AxolotlAddress address) {
        sessions.remove(address);
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        for (AxolotlAddress key : sessions.keySet()) {
            if (key.getName().equals(name)) {
                sessions.remove(key);
            }
        }
    }
}
