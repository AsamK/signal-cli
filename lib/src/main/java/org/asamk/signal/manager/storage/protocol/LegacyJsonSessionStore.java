package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.asamk.signal.manager.util.Utils;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class LegacyJsonSessionStore {

    private final List<LegacySessionInfo> sessions;

    private LegacyJsonSessionStore(final List<LegacySessionInfo> sessions) {
        this.sessions = sessions;
    }

    public List<LegacySessionInfo> getSessions() {
        return sessions;
    }

    public static class JsonSessionStoreDeserializer extends JsonDeserializer<LegacyJsonSessionStore> {

        @Override
        public LegacyJsonSessionStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var sessions = new ArrayList<LegacySessionInfo>();

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
                    var sessionInfo = new LegacySessionInfo(serviceAddress, deviceId, record);
                    sessions.add(sessionInfo);
                }
            }

            return new LegacyJsonSessionStore(sessions);
        }
    }
}
