package org.asamk.signal.mqtt;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.asamk.signal.JsonError;
import org.asamk.signal.JsonMessageEnvelope;
import org.asamk.signal.manager.Manager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

/**
 * Handler class that passes incoming Signal messages to an mqtt broker.
 */
public class MqttReceiveMessageHandler implements Manager.ReceiveMessageHandler {

    public static String DEFAULT_TOPIC = "signal-cli/messages/incoming";

    private static int DEFAULT_QUALITY_OF_SERVICE = 2;

    private final MqttClient mqttClient;
    private final ObjectMapper jsonProcessor;

    /**
     * Creates a new instance that passes all incoming messages to the provided mqttClient.
     * @param mqttClient the broker to pass all the incoming messages to
     */
    public MqttReceiveMessageHandler(MqttClient mqttClient)
    {
        this.mqttClient = mqttClient;
        this.jsonProcessor = new ObjectMapper();
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
        jsonProcessor.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    /**
     * Builds a Json Message from an incoming signal message.
     * @param envelope the signal service envelope of the message
     * @param content the content of the message
     * @param exception an exception that might have occurred on the way of processing
     * @return the json encoded message as a string
     */
    private String buildJsonMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        ObjectNode result = jsonProcessor.createObjectNode();
        if (exception != null) {
            result.putPOJO("error", new JsonError(exception));
        }
        if (envelope != null) {
            result.putPOJO("envelope", new JsonMessageEnvelope(envelope, content));
        }
        try {
            return jsonProcessor.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            ObjectNode errorMsg = jsonProcessor.createObjectNode();
            result.putPOJO("error", new JsonError(e));
            try {
                return jsonProcessor.writeValueAsString(errorMsg);
            } catch (JsonProcessingException jsonEx) {
                // this should never happen, but well just to be safe
                throw new AssertionError(jsonEx);
            }
        }
    }

    @Override
    public void handleMessage(final SignalServiceEnvelope envelope, final SignalServiceContent decryptedContent, final Throwable e) {
        System.out.println("Sender: " + decryptedContent.getSender());
        System.out.println("Message Received");
        String content = buildJsonMessage(envelope, decryptedContent, e);
        System.out.println("Publishing message: " + content);
        MqttMessage message = new MqttMessage(content.getBytes());
        message.setQos(DEFAULT_QUALITY_OF_SERVICE);

        try {
            mqttClient.publish(DEFAULT_TOPIC, message);
        } catch (MqttException e1) {
            e1.printStackTrace();
            // TODO: not sure how to handle that here
        }
        System.out.println("Message published");
    }
}
