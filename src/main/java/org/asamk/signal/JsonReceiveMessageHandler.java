package org.asamk.signal;

import org.asamk.signal.json.JsonError;
import org.asamk.signal.json.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class JsonReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    private final static Logger logger = LoggerFactory.getLogger(JsonReceiveMessageHandler.class);

    private final Manager m;
    private final JsonWriter jsonWriter;

    public JsonReceiveMessageHandler(Manager m, JsonWriter jsonWriter) {
        this.m = m;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public void handleMessage(MessageEnvelope envelope, Throwable exception) {
        final var object = new HashMap<String, Object>();
        object.put("account", m.getSelfNumber());
        if (exception != null) {
            object.put("error", JsonError.from(exception));
        }

        if (envelope != null) {
            object.put("envelope", JsonMessageEnvelope.from(envelope, exception, m));
        }

        jsonWriter.write(object);
    }
}
