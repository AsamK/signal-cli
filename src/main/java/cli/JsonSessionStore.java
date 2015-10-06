package cli;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;

import java.io.IOException;
import java.util.*;

class JsonSessionStore implements SessionStore {

    private final Map<AxolotlAddress, byte[]> sessions = new HashMap<>();

    public JsonSessionStore() {

    }

    public void addSessions(Map<AxolotlAddress, byte[]> sessions) {
        this.sessions.putAll(sessions);
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
        for (AxolotlAddress key : new ArrayList<>(sessions.keySet())) {
            if (key.getName().equals(name)) {
                sessions.remove(key);
            }
        }
    }

    public static class JsonSessionStoreDeserializer extends JsonDeserializer<JsonSessionStore> {

        @Override
        public JsonSessionStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Map<AxolotlAddress, byte[]> sessionMap = new HashMap<>();
            if (node.isArray()) {
                for (JsonNode session : node) {
                    String sessionName = session.get("name").asText();
                    try {
                        sessionMap.put(new AxolotlAddress(sessionName, session.get("deviceId").asInt()), Base64.decode(session.get("record").asText()));
                    }  catch (IOException e) {
                        System.out.println(String.format("Error while decoding session for: %s", sessionName));
                    }
                }
            }

            JsonSessionStore sessionStore = new JsonSessionStore();
            sessionStore.addSessions(sessionMap);

            return sessionStore;

        }
    }

    public static class JsonPreKeyStoreSerializer extends JsonSerializer<JsonSessionStore> {

        @Override
        public void serialize(JsonSessionStore jsonSessionStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            json.writeStartArray();
            for (Map.Entry<AxolotlAddress, byte[]> preKey : jsonSessionStore.sessions.entrySet()) {
                json.writeStartObject();
                json.writeStringField("name", preKey.getKey().getName());
                json.writeNumberField("deviceId", preKey.getKey().getDeviceId());
                json.writeStringField("record", Base64.encodeBytes(preKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
