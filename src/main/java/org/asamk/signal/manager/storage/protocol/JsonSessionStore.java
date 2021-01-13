package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

class JsonSessionStore implements SessionStore {

    private final static Logger logger = LoggerFactory.getLogger(JsonSessionStore.class);

    private final List<SessionInfo> sessions = new ArrayList<>();

    private SignalServiceAddressResolver resolver;

    public JsonSessionStore() {
    }

    public void setResolver(final SignalServiceAddressResolver resolver) {
        this.resolver = resolver;
    }

    private SignalServiceAddress resolveSignalServiceAddress(String identifier) {
        if (resolver != null) {
            return resolver.resolveSignalServiceAddress(identifier);
        } else {
            return Utils.getSignalServiceAddressFromIdentifier(identifier);
        }
    }

    @Override
    public synchronized SessionRecord loadSession(SignalProtocolAddress address) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
        for (SessionInfo info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
                try {
                    return new SessionRecord(info.sessionRecord);
                } catch (IOException e) {
                    logger.warn("Failed to load session, resetting session: {}", e.getMessage());
                    final SessionRecord sessionRecord = new SessionRecord();
                    info.sessionRecord = sessionRecord.serialize();
                    return sessionRecord;
                }
            }
        }

        return new SessionRecord();
    }

    public synchronized List<SessionInfo> getSessions() {
        return sessions;
    }

    @Override
    public synchronized List<Integer> getSubDeviceSessions(String name) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(name);

        List<Integer> deviceIds = new LinkedList<>();
        for (SessionInfo info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId != 1) {
                deviceIds.add(info.deviceId);
            }
        }

        return deviceIds;
    }

    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
        for (SessionInfo info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
                if (!info.address.getUuid().isPresent() || !info.address.getNumber().isPresent()) {
                    info.address = serviceAddress;
                }
                info.sessionRecord = record.serialize();
                return;
            }
        }

        sessions.add(new SessionInfo(serviceAddress, address.getDeviceId(), record.serialize()));
    }

    @Override
    public synchronized boolean containsSession(SignalProtocolAddress address) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
        for (SessionInfo info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void deleteSession(SignalProtocolAddress address) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
        sessions.removeIf(info -> info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId());
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        SignalServiceAddress serviceAddress = resolveSignalServiceAddress(name);
        deleteAllSessions(serviceAddress);
    }

    public synchronized void deleteAllSessions(SignalServiceAddress serviceAddress) {
        sessions.removeIf(info -> info.address.matches(serviceAddress));
    }

    public static class JsonSessionStoreDeserializer extends JsonDeserializer<JsonSessionStore> {

        @Override
        public JsonSessionStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            JsonSessionStore sessionStore = new JsonSessionStore();

            if (node.isArray()) {
                for (JsonNode session : node) {
                    String sessionName = session.hasNonNull("name") ? session.get("name").asText() : null;
                    if (UuidUtil.isUuid(sessionName)) {
                        // Ignore sessions that were incorrectly created with UUIDs as name
                        continue;
                    }

                    UUID uuid = session.hasNonNull("uuid") ? UuidUtil.parseOrNull(session.get("uuid").asText()) : null;
                    final SignalServiceAddress serviceAddress = uuid == null
                            ? Utils.getSignalServiceAddressFromIdentifier(sessionName)
                            : new SignalServiceAddress(uuid, sessionName);
                    final int deviceId = session.get("deviceId").asInt();
                    final String record = session.get("record").asText();
                    try {
                        SessionInfo sessionInfo = new SessionInfo(serviceAddress, deviceId, Base64.decode(record));
                        sessionStore.sessions.add(sessionInfo);
                    } catch (IOException e) {
                        logger.warn("Error while decoding session for {}: {}", sessionName, e.getMessage());
                    }
                }
            }

            return sessionStore;
        }
    }

    public static class JsonSessionStoreSerializer extends JsonSerializer<JsonSessionStore> {

        @Override
        public void serialize(
                JsonSessionStore jsonSessionStore, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartArray();
            for (SessionInfo sessionInfo : jsonSessionStore.sessions) {
                json.writeStartObject();
                if (sessionInfo.address.getNumber().isPresent()) {
                    json.writeStringField("name", sessionInfo.address.getNumber().get());
                }
                if (sessionInfo.address.getUuid().isPresent()) {
                    json.writeStringField("uuid", sessionInfo.address.getUuid().get().toString());
                }
                json.writeNumberField("deviceId", sessionInfo.deviceId);
                json.writeStringField("record", Base64.encodeBytes(sessionInfo.sessionRecord));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }

}
