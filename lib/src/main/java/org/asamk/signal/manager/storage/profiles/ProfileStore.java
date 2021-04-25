package org.asamk.signal.manager.storage.profiles;

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
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ProfileStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty("profiles")
    @JsonDeserialize(using = ProfileStoreDeserializer.class)
    @JsonSerialize(using = ProfileStoreSerializer.class)
    private final List<SignalProfileEntry> profiles = new ArrayList<>();

    public SignalProfileEntry getProfileEntry(SignalServiceAddress serviceAddress) {
        for (var entry : profiles) {
            if (entry.getServiceAddress().matches(serviceAddress)) {
                return entry;
            }
        }
        return null;
    }

    public ProfileKey getProfileKey(SignalServiceAddress serviceAddress) {
        for (var entry : profiles) {
            if (entry.getServiceAddress().matches(serviceAddress)) {
                return entry.getProfileKey();
            }
        }
        return null;
    }

    public void updateProfile(
            SignalServiceAddress serviceAddress,
            ProfileKey profileKey,
            long now,
            SignalProfile profile,
            ProfileKeyCredential profileKeyCredential
    ) {
        var newEntry = new SignalProfileEntry(serviceAddress, profileKey, now, profile, profileKeyCredential);
        for (var i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getServiceAddress().matches(serviceAddress)) {
                profiles.set(i, newEntry);
                return;
            }
        }

        profiles.add(newEntry);
    }

    public void storeProfileKey(SignalServiceAddress serviceAddress, ProfileKey profileKey) {
        var newEntry = new SignalProfileEntry(serviceAddress, profileKey, 0, null, null);
        for (var i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getServiceAddress().matches(serviceAddress)) {
                if (!profiles.get(i).getProfileKey().equals(profileKey)) {
                    profiles.set(i, newEntry);
                }
                return;
            }
        }

        profiles.add(newEntry);
    }

    public static class ProfileStoreDeserializer extends JsonDeserializer<List<SignalProfileEntry>> {

        @Override
        public List<SignalProfileEntry> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var addresses = new ArrayList<SignalProfileEntry>();

            if (node.isArray()) {
                for (var entry : node) {
                    var name = entry.hasNonNull("name") ? entry.get("name").asText() : null;
                    var uuid = entry.hasNonNull("uuid") ? UuidUtil.parseOrNull(entry.get("uuid").asText()) : null;
                    final var serviceAddress = new SignalServiceAddress(uuid, name);
                    ProfileKey profileKey = null;
                    try {
                        profileKey = new ProfileKey(Base64.getDecoder().decode(entry.get("profileKey").asText()));
                    } catch (InvalidInputException ignored) {
                    }
                    ProfileKeyCredential profileKeyCredential = null;
                    if (entry.hasNonNull("profileKeyCredential")) {
                        try {
                            profileKeyCredential = new ProfileKeyCredential(Base64.getDecoder()
                                    .decode(entry.get("profileKeyCredential").asText()));
                        } catch (Throwable ignored) {
                        }
                    }
                    var lastUpdateTimestamp = entry.get("lastUpdateTimestamp").asLong();
                    var profile = jsonProcessor.treeToValue(entry.get("profile"), SignalProfile.class);
                    addresses.add(new SignalProfileEntry(serviceAddress,
                            profileKey,
                            lastUpdateTimestamp,
                            profile,
                            profileKeyCredential));
                }
            }

            return addresses;
        }
    }

    public static class ProfileStoreSerializer extends JsonSerializer<List<SignalProfileEntry>> {

        @Override
        public void serialize(
                List<SignalProfileEntry> profiles, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartArray();
            for (var profileEntry : profiles) {
                final var address = profileEntry.getServiceAddress();
                json.writeStartObject();
                if (address.getNumber().isPresent()) {
                    json.writeStringField("name", address.getNumber().get());
                }
                if (address.getUuid().isPresent()) {
                    json.writeStringField("uuid", address.getUuid().get().toString());
                }
                json.writeStringField("profileKey",
                        Base64.getEncoder().encodeToString(profileEntry.getProfileKey().serialize()));
                json.writeNumberField("lastUpdateTimestamp", profileEntry.getLastUpdateTimestamp());
                json.writeObjectField("profile", profileEntry.getProfile());
                if (profileEntry.getProfileKeyCredential() != null) {
                    json.writeStringField("profileKeyCredential",
                            Base64.getEncoder().encodeToString(profileEntry.getProfileKeyCredential().serialize()));
                }
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
