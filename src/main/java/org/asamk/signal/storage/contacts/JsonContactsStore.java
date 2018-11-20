package org.asamk.signal.storage.contacts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonContactsStore {

    private static final ObjectMapper jsonProcessor = new ObjectMapper();
    @JsonProperty("contacts")
    @JsonSerialize(using = JsonContactsStore.MapToListSerializer.class)
    @JsonDeserialize(using = ContactsDeserializer.class)
    private Map<String, ContactInfo> contacts = new HashMap<>();

    public void updateContact(ContactInfo contact) {
        contacts.put(contact.number, contact);
    }

    public ContactInfo getContact(String number) {
        return contacts.get(number);
    }

    public List<ContactInfo> getContacts() {
        return new ArrayList<>(contacts.values());
    }

    /**
     * Remove all contacts from the store
     */
    public void clear() {
        contacts.clear();
    }

    private static class MapToListSerializer extends JsonSerializer<Map<?, ?>> {

        @Override
        public void serialize(final Map<?, ?> value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
            jgen.writeObject(value.values());
        }
    }

    private static class ContactsDeserializer extends JsonDeserializer<Map<String, ContactInfo>> {

        @Override
        public Map<String, ContactInfo> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            Map<String, ContactInfo> contacts = new HashMap<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode n : node) {
                ContactInfo c = jsonProcessor.treeToValue(n, ContactInfo.class);
                contacts.put(c.number, c);
            }

            return contacts;
        }
    }
}
