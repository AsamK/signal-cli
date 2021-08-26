package org.asamk.signal.manager.storage.recipients;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LegacyRecipientStore {

    @JsonProperty("recipientStore")
    @JsonDeserialize(using = RecipientStoreDeserializer.class)
    private final List<RecipientAddress> addresses = new ArrayList<>();

    public List<RecipientAddress> getAddresses() {
        return addresses;
    }

    public static class RecipientStoreDeserializer extends JsonDeserializer<List<RecipientAddress>> {

        @Override
        public List<RecipientAddress> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var addresses = new ArrayList<RecipientAddress>();

            if (node.isArray()) {
                for (var recipient : node) {
                    var recipientName = recipient.get("name").asText();
                    var uuid = UuidUtil.parseOrThrow(recipient.get("uuid").asText());
                    addresses.add(new RecipientAddress(uuid, recipientName));
                }
            }

            return addresses;
        }
    }
}
