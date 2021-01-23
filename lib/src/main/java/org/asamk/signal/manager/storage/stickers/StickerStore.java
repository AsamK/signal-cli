package org.asamk.signal.manager.storage.stickers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StickerStore {

    @JsonSerialize(using = StickersSerializer.class)
    @JsonDeserialize(using = StickersDeserializer.class)
    private final Map<byte[], Sticker> stickers = new HashMap<>();

    public Sticker getSticker(byte[] packId) {
        return stickers.get(packId);
    }

    public void updateSticker(Sticker sticker) {
        stickers.put(sticker.getPackId(), sticker);
    }

    private static class StickersSerializer extends JsonSerializer<Map<byte[], Sticker>> {

        @Override
        public void serialize(
                final Map<byte[], Sticker> value, final JsonGenerator jgen, final SerializerProvider provider
        ) throws IOException {
            final Collection<Sticker> stickers = value.values();
            jgen.writeStartArray(stickers.size());
            for (Sticker sticker : stickers) {
                jgen.writeStartObject();
                jgen.writeStringField("packId", Base64.getEncoder().encodeToString(sticker.getPackId()));
                jgen.writeStringField("packKey", Base64.getEncoder().encodeToString(sticker.getPackKey()));
                jgen.writeBooleanField("installed", sticker.isInstalled());
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
        }
    }

    private static class StickersDeserializer extends JsonDeserializer<Map<byte[], Sticker>> {

        @Override
        public Map<byte[], Sticker> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            Map<byte[], Sticker> stickers = new HashMap<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                byte[] packId = Base64.getDecoder().decode(n.get("packId").asText());
                byte[] packKey = Base64.getDecoder().decode(n.get("packKey").asText());
                boolean installed = n.get("installed").asBoolean(false);
                stickers.put(packId, new Sticker(packId, packKey, installed));
            }

            return stickers;
        }
    }
}
