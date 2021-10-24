package org.asamk.signal;

import org.asamk.signal.json.JsonError;
import org.asamk.signal.json.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.util.HashMap;

public class JsonReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    private final static Logger logger = LoggerFactory.getLogger(JsonReceiveMessageHandler.class);

    protected final Manager m;
    private final JsonWriter jsonWriter;

    public JsonReceiveMessageHandler(Manager m, JsonWriter jsonWriter) {
        this.m = m;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        final var object = new HashMap<String, Object>();
        if (exception != null) {
            object.put("error", JsonError.from(exception));
        }

        if (envelope != null) {
            object.put("envelope", JsonMessageEnvelope.from(envelope, content, exception, m));
        }

        jsonWriter.write(object);
    }
}
