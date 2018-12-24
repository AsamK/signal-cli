package org.asamk.signal.mqtt;

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

    public static String DEFAULT_TOPIC = "signal-cli/messages/incoming/";

    private static int DEFAULT_QUALITY_OF_SERVICE = 2;

    private final MqttClient mqttClient;
    private final Manager manager;


    /**
     * Creates a new instance that passes all incoming messages to the provided mqttClient.
     * @param mqttClient the broker to pass all the incoming messages to
     */
    public MqttReceiveMessageHandler(Manager manager, MqttClient mqttClient)
    {
        this.manager = manager;
        this.mqttClient = mqttClient;
    }

    /**
     * Removes spaces and wildcard signs (*, +) from a given string.
     * @param topic the topic to clean
     * @return the cleaned topic
     */
    private String stripIllegalTopicCharacters(String topic)
    {
        return topic.replace("+", "")
                .replace(" ", "");
    }

    private void publishMessage(String topic, String content)
    {
        MqttMessage message = new MqttMessage(content.getBytes());
        message.setQos(DEFAULT_QUALITY_OF_SERVICE);
        try {
            System.out.println("Topic: " + topic);
            System.out.println("Publishing message: " + content);
            mqttClient.publish(topic, message);
        } catch (MqttException ex) {
            throw new AssertionError(ex);
        }
        System.out.println("Message published");
    }

    @Override
    public void handleMessage(final SignalServiceEnvelope envelope, final SignalServiceContent decryptedContent, final Throwable e) {
        System.out.println("Message Received from " + decryptedContent.getSender());

        MqttJsonMessage msg = MqttJsonMessage.build(envelope, decryptedContent, e);
        String topic = DEFAULT_TOPIC + stripIllegalTopicCharacters(manager.getUsername() + "/" + msg.getSubTopic());

        publishMessage(topic, msg.getJsonContent());

    }
}
