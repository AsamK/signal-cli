package org.asamk.signal.storage.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RecipientStore {

    @JsonProperty("recipientStore")
    @JsonDeserialize(using = RecipientStoreDeserializer.class)
    @JsonSerialize(using = RecipientStoreSerializer.class)
    private final Set<SignalServiceAddress> addresses = new HashSet<>();

    public SignalServiceAddress resolveServiceAddress(SignalServiceAddress serviceAddress) {
        if (addresses.contains(serviceAddress)) {
            // If the Set already contains the exact address with UUID and Number,
            // we can just return it here.
            return serviceAddress;
        }

        for (SignalServiceAddress address : addresses) {
            if (address.matches(serviceAddress)) {
                return address;
            }
        }

        if (serviceAddress.getNumber().isPresent() && serviceAddress.getUuid().isPresent()) {
            addresses.add(serviceAddress);
        }

        return serviceAddress;
    }

    public static class RecipientStoreDeserializer extends JsonDeserializer<Set<SignalServiceAddress>> {

        @Override
        public Set<SignalServiceAddress> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            Set<SignalServiceAddress> addresses = new HashSet<>();

            if (node.isArray()) {
                for (JsonNode recipient : node) {
                    String recipientName = recipient.get("name").asText();
                    UUID uuid = UuidUtil.parseOrThrow(recipient.get("uuid").asText());
                    final SignalServiceAddress serviceAddress = new SignalServiceAddress(uuid, recipientName);
                    addresses.add(serviceAddress);
                }
            }

            return addresses;
        }
    }

    public static class RecipientStoreSerializer extends JsonSerializer<Set<SignalServiceAddress>> {

        @Override
        public void serialize(
                Set<SignalServiceAddress> addresses, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartArray();
            for (SignalServiceAddress address : addresses) {
                json.writeStartObject();
                json.writeStringField("name", address.getNumber().get());
                json.writeStringField("uuid", address.getUuid().get().toString());
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
