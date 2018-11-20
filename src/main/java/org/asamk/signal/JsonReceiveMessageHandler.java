package org.asamk.signal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;

public class JsonReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    final Manager m;
    private final ObjectMapper jsonProcessor;

    public JsonReceiveMessageHandler(Manager m) {
        this.m = m;
        this.jsonProcessor = new ObjectMapper();
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
        jsonProcessor.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        ObjectNode result = jsonProcessor.createObjectNode();
        if (exception != null) {
            result.putPOJO("error", new JsonError(exception));
        }
        if (envelope != null) {
            result.putPOJO("envelope", new JsonMessageEnvelope(envelope, content));
        }
        try {
            jsonProcessor.writeValue(System.out, result);
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
