package org.asamk.signal.storage.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty("profiles")
    @JsonDeserialize(using = ProfileStoreDeserializer.class)
    @JsonSerialize(using = ProfileStoreSerializer.class)
    private final Map<SignalServiceAddress, SignalProfileEntry> profiles = new HashMap<>();

    public SignalProfileEntry getProfile(SignalServiceAddress serviceAddress) {
        return profiles.get(serviceAddress);
    }

    public void updateProfile(SignalServiceAddress serviceAddress, SignalProfileEntry profile) {
        profiles.put(serviceAddress, profile);
    }

    public static class ProfileStoreDeserializer extends JsonDeserializer<Map<SignalServiceAddress, SignalProfileEntry>> {

        @Override
        public Map<SignalServiceAddress, SignalProfileEntry> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Map<SignalServiceAddress, SignalProfileEntry> addresses = new HashMap<>();

            if (node.isArray()) {
                for (JsonNode entry : node) {
                    String name = entry.hasNonNull("name")
                            ? entry.get("name").asText()
                            : null;
                    UUID uuid = entry.hasNonNull("uuid")
                            ? UuidUtil.parseOrNull(entry.get("uuid").asText())
                            : null;
                    final SignalServiceAddress serviceAddress = new SignalServiceAddress(uuid, name);
                    ProfileKey profileKey = null;
                    try {
                        profileKey = new ProfileKey(Base64.decode(entry.get("profileKey").asText()));
                    } catch (InvalidInputException ignored) {
                    }
                    long lastUpdateTimestamp = entry.get("lastUpdateTimestamp").asLong();
                    SignalProfile profile = jsonProcessor.treeToValue(entry.get("profile"), SignalProfile.class);
                    addresses.put(serviceAddress, new SignalProfileEntry(profileKey, lastUpdateTimestamp, profile));
                }
            }

            return addresses;
        }
    }

    public static class ProfileStoreSerializer extends JsonSerializer<Map<SignalServiceAddress, SignalProfileEntry>> {

        @Override
        public void serialize(Map<SignalServiceAddress, SignalProfileEntry> profiles, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for (Map.Entry<SignalServiceAddress, SignalProfileEntry> entry : profiles.entrySet()) {
                final SignalServiceAddress address = entry.getKey();
                final SignalProfileEntry profileEntry = entry.getValue();
                json.writeStartObject();
                if (address.getNumber().isPresent()) {
                    json.writeStringField("name", address.getNumber().get());
                }
                if (address.getUuid().isPresent()) {
                    json.writeStringField("uuid", address.getUuid().get().toString());
                }
                json.writeStringField("profileKey", Base64.encodeBytes(profileEntry.getProfileKey().serialize()));
                json.writeNumberField("lastUpdateTimestamp", profileEntry.getLastUpdateTimestamp());
                json.writeObjectField("profile", profileEntry.getProfile());
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
