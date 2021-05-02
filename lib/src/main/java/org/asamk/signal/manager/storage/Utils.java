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
        final ObjectMapper jsonProcessor = new ObjectMapper();

        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        jsonProcessor.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        return jsonProcessor;
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
