package org.asamk.signal.manager.storage.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class LegacyProfileStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    @JsonProperty("profiles")
    @JsonDeserialize(using = ProfileStoreDeserializer.class)
    private final List<LegacySignalProfileEntry> profiles = new ArrayList<>();

    public List<LegacySignalProfileEntry> getProfileEntries() {
        return profiles;
    }

    public static class ProfileStoreDeserializer extends JsonDeserializer<List<LegacySignalProfileEntry>> {

        @Override
        public List<LegacySignalProfileEntry> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var profileEntries = new ArrayList<LegacySignalProfileEntry>();

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
                    profileEntries.add(new LegacySignalProfileEntry(serviceAddress,
                            profileKey,
                            lastUpdateTimestamp,
                            profile,
                            profileKeyCredential));
                }
            }

            return profileEntries;
        }
    }
}
