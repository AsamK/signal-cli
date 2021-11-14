package org.asamk.signal.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.util.Util;

import java.io.IOException;
import java.io.Writer;

public class JsonWriterImpl implements JsonWriter {

    private final Writer writer;
    private final ObjectMapper objectMapper;

    public JsonWriterImpl(final Writer writer) {
        this.writer = writer;
        this.objectMapper = Util.createJsonObjectMapper();
    }

    public synchronized void write(final Object object) {
        try {
            try {
                objectMapper.writeValue(writer, object);
            } catch (JsonProcessingException e) {
                // Some issue with json serialization, probably caused by a bug
                throw new AssertionError(e);
            }
            writer.write(System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
