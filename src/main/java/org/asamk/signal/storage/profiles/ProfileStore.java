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

    public SignalProfileEntry updateProfile(SignalServiceAddress serviceAddress, SignalProfileEntry profile) {
        return profiles.put(serviceAddress, profile);
    }

    public static class ProfileStoreDeserializer extends JsonDeserializer<Map<SignalServiceAddress, SignalProfileEntry>> {

        @Override
        public Map<SignalServiceAddress, SignalProfileEntry> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Map<SignalServiceAddress, SignalProfileEntry> addresses = new HashMap<>();

            if (node.isArray()) {
                for (JsonNode recipient : node) {
                    String recipientName = recipient.get("name").asText();
                    UUID uuid = UuidUtil.parseOrThrow(recipient.get("uuid").asText());
                    final SignalServiceAddress serviceAddress = new SignalServiceAddress(uuid, recipientName);
                    ProfileKey profileKey = null;
                    try {
                        profileKey = new ProfileKey(Base64.decode(recipient.get("profileKey").asText()));
                    } catch (InvalidInputException ignored) {
                    }
                    long lastUpdateTimestamp = recipient.get("lastUpdateTimestamp").asLong();
                    SignalProfile profile = jsonProcessor.treeToValue(recipient.get("profile"), SignalProfile.class);
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
                json.writeStringField("name", address.getNumber().get());
                json.writeStringField("uuid", address.getUuid().get().toString());
                json.writeStringField("profileKey", Base64.encodeBytes(profileEntry.getProfileKey().serialize()));
                json.writeNumberField("lastUpdateTimestamp", profileEntry.getLastUpdateTimestamp());
                json.writeObjectField("profile", profileEntry.getProfile());
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
