package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class JsonWriter {

    private final OutputStreamWriter writer;
    private final ObjectMapper objectMapper;

    public JsonWriter(final OutputStream writer) {
        this.writer = new OutputStreamWriter(writer, StandardCharsets.UTF_8);

        objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public void write(final Object object) throws IOException {
        try {
            objectMapper.writeValue(writer, object);
        } catch (JsonProcessingException e) {
            // Some issue with json serialization, probably caused by a bug
            throw new AssertionError(e);
        }
        writer.write(System.lineSeparator());
        writer.flush();
    }
}
