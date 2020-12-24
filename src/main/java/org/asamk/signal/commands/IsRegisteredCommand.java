package org.asamk.signal.commands;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;

import java.io.IOException;

public class IsRegisteredCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number").help("Phone number").nargs("+");
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("--json")
                .help("Output received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        ObjectMapper jsonProcessor = new ObjectMapper();
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        for (String phone_number : ns.<String>getList("number")) {
            try {
                boolean isRegistered = m.isUserRegistered(phone_number);
                if (ns.getBoolean("json")) {
                    JsonIsReceived object = new JsonIsReceived(phone_number, isRegistered);
                    System.out.println(jsonProcessor.writeValueAsString(object));
                } else {
                    System.out.println(phone_number + ": " + isRegistered);
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }

        return 0;
    }

    private class JsonIsReceived {
        String name;
        boolean isRegistered;

        public JsonIsReceived(String name, boolean isRegistered) {
            this.name = name;
            this.isRegistered = isRegistered;
        }
    }

}
