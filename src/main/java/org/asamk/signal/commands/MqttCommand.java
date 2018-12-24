package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.mqtt.MqttReceiveMessageHandler;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class MqttCommand implements LocalCommand {

    private static String DEFAULT_MQTT_BROKER = "tcp://127.0.0.1:1883";

    @Override
    public void attachToSubparser(final Subparser subparser) {
        //subparser.addArgument("--json")
        //       .help("Output received messages in json format, one json object per line.")
        //      .action(Arguments.storeTrue());
        subparser.addArgument("-b", "--broker")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.store());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }
        // TODO: start new thread to also send messages
        String brokerInput = ns.getString("broker");

        String broker  = brokerInput != null ? brokerInput : DEFAULT_MQTT_BROKER;
        String clientId     = "signal-cli";

        MqttClient mqttClient = null;
        try {
            // connect to mqtt
            mqttClient = new MqttClient(broker, clientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: " + broker);
            mqttClient.connect(connOpts);
            System.out.println("Connected");
            boolean ignoreAttachments = false;
            try {
                m.receiveMessages(1,
                        TimeUnit.HOURS,
                        false,
                        ignoreAttachments,
                        new MqttReceiveMessageHandler(mqttClient));
                return 0;
            } catch (IOException e) {
                System.err.println("Error while receiving messages: " + e.getMessage());
                return 3;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            }
        } catch(MqttException me) {
            System.err.println("Error while handling mqtt: " + me.getMessage());
            me.printStackTrace();
            return 1;
        } finally {
           if(mqttClient != null) {
               try {
                   mqttClient.disconnect();
                   return 0;
               }
               catch (MqttException me)
               {
                   System.err.println("Error while closing mqtt connection: " + me.getMessage());
                   return 1;
               }
           }
        }
    }
}
