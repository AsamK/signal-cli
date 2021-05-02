package org.asamk.signal.manager.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InvalidObjectException;

public class Utils {

    private Utils() {
    }

    public static ObjectMapper createStorageObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        return objectMapper;
    }

    public static JsonNode getNotNullNode(JsonNode parent, String name) throws InvalidObjectException {
        var node = parent.get(name);
        if (node == null || node.isNull()) {
            throw new InvalidObjectException(String.format("Incorrect file format: expected parameter %s not found ",
                    name));
        }

        return node;
    }
}
