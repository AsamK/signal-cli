package org.asamk.signal.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.io.InputStream;

public class JsonStreamSerializer extends JsonSerializer<InputStream> {

    @Override
    public void serialize(
            final InputStream value, final JsonGenerator jsonGenerator, final SerializerProvider serializers
    ) throws IOException {
        jsonGenerator.writeBinary(value, -1);
    }
}
