package org.asamk.signal.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.IOException;
import java.util.*;

class JsonSessionStore implements SessionStore {

    private final Map<SignalProtocolAddress, byte[]> sessions = new HashMap<>();

    public JsonSessionStore() {

    }

    private void addSessions(Map<SignalProtocolAddress, byte[]> sessions) {
        this.sessions.putAll(sessions);
    }

    @Override
    public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
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

        for (SignalProtocolAddress key : sessions.keySet()) {
            if (key.getName().equals(name) &&
                    key.getDeviceId() != 1) {
                deviceIds.add(key.getDeviceId());
            }
        }

        return deviceIds;
    }

    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessions.put(address, record.serialize());
    }

    @Override
    public synchronized boolean containsSession(SignalProtocolAddress address) {
        return sessions.containsKey(address);
    }

    @Override
    public synchronized void deleteSession(SignalProtocolAddress address) {
        sessions.remove(address);
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        for (SignalProtocolAddress key : new ArrayList<>(sessions.keySet())) {
            if (key.getName().equals(name)) {
                sessions.remove(key);
            }
        }
    }

    public static class JsonSessionStoreDeserializer extends JsonDeserializer<JsonSessionStore> {

        @Override
        public JsonSessionStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Map<SignalProtocolAddress, byte[]> sessionMap = new HashMap<>();
            if (node.isArray()) {
                for (JsonNode session : node) {
                    String sessionName = session.get("name").asText();
                    try {
                        sessionMap.put(new SignalProtocolAddress(sessionName, session.get("deviceId").asInt()), Base64.decode(session.get("record").asText()));
                    } catch (IOException e) {
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
        public void serialize(JsonSessionStore jsonSessionStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for (Map.Entry<SignalProtocolAddress, byte[]> preKey : jsonSessionStore.sessions.entrySet()) {
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
