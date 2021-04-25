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
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

class JsonSessionStore implements SignalServiceSessionStore {

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
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        for (var info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
                try {
                    return new SessionRecord(info.sessionRecord);
                } catch (IOException e) {
                    logger.warn("Failed to load session, resetting session: {}", e.getMessage());
                    return new SessionRecord();
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
        var serviceAddress = resolveSignalServiceAddress(name);

        var deviceIds = new LinkedList<Integer>();
        for (var info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId != 1) {
                deviceIds.add(info.deviceId);
            }
        }

        return deviceIds;
    }

    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        for (var info : sessions) {
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
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        for (var info : sessions) {
            if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
                final SessionRecord sessionRecord;
                try {
                    sessionRecord = new SessionRecord(info.sessionRecord);
                } catch (IOException e) {
                    logger.warn("Failed to check session: {}", e.getMessage());
                    return false;
                }

                return sessionRecord.hasSenderChain()
                        && sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
            }
        }
        return false;
    }

    @Override
    public synchronized void deleteSession(SignalProtocolAddress address) {
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        sessions.removeIf(info -> info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId());
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        var serviceAddress = resolveSignalServiceAddress(name);
        deleteAllSessions(serviceAddress);
    }

    public synchronized void deleteAllSessions(SignalServiceAddress serviceAddress) {
        sessions.removeIf(info -> info.address.matches(serviceAddress));
    }

    @Override
    public void archiveSession(final SignalProtocolAddress address) {
        final var sessionRecord = loadSession(address);
        if (sessionRecord == null) {
            return;
        }
        sessionRecord.archiveCurrentState();
        storeSession(address, sessionRecord);
    }

    public void archiveAllSessions() {
        for (var info : sessions) {
            try {
                final var sessionRecord = new SessionRecord(info.sessionRecord);
                sessionRecord.archiveCurrentState();
                info.sessionRecord = sessionRecord.serialize();
            } catch (IOException ignored) {
            }
        }
    }

    public static class JsonSessionStoreDeserializer extends JsonDeserializer<JsonSessionStore> {

        @Override
        public JsonSessionStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var sessionStore = new JsonSessionStore();

            if (node.isArray()) {
                for (var session : node) {
                    var sessionName = session.hasNonNull("name") ? session.get("name").asText() : null;
                    if (UuidUtil.isUuid(sessionName)) {
                        // Ignore sessions that were incorrectly created with UUIDs as name
                        continue;
                    }

                    var uuid = session.hasNonNull("uuid") ? UuidUtil.parseOrNull(session.get("uuid").asText()) : null;
                    final var serviceAddress = uuid == null
                            ? Utils.getSignalServiceAddressFromIdentifier(sessionName)
                            : new SignalServiceAddress(uuid, sessionName);
                    final var deviceId = session.get("deviceId").asInt();
                    final var record = Base64.getDecoder().decode(session.get("record").asText());
                    var sessionInfo = new SessionInfo(serviceAddress, deviceId, record);
                    sessionStore.sessions.add(sessionInfo);
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
            for (var sessionInfo : jsonSessionStore.sessions) {
                json.writeStartObject();
                if (sessionInfo.address.getNumber().isPresent()) {
                    json.writeStringField("name", sessionInfo.address.getNumber().get());
                }
                if (sessionInfo.address.getUuid().isPresent()) {
                    json.writeStringField("uuid", sessionInfo.address.getUuid().get().toString());
                }
                json.writeNumberField("deviceId", sessionInfo.deviceId);
                json.writeStringField("record", Base64.getEncoder().encodeToString(sessionInfo.sessionRecord));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }

}
