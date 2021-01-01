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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;

public class GetUserStatusCommand implements LocalCommand {

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

        // Setup the json object mapper
        ObjectMapper jsonProcessor = new ObjectMapper();
        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        // Get a map of registration statuses
        Map<String, Boolean> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(ns.<String>getList("number")));
        } catch (IOException e) {
            System.err.println("Unable to check if users are registered");
            return 1;
        }

        // Output
        if (ns.getBoolean("json")) {
            List<JsonIsRegistered> objects = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : registered.entrySet()) {
                objects.add(new JsonIsRegistered(entry.getKey(), entry.getValue()));
            }

            try {
                System.out.println(jsonProcessor.writeValueAsString(objects));
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }

        } else {
            for (Map.Entry<String, Boolean> entry : registered.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }

        return 0;
    }

    private class JsonIsRegistered {
        String name;
        boolean isRegistered;

        public JsonIsRegistered(String name, boolean isRegistered) {
            this.name = name;
            this.isRegistered = isRegistered;
        }
    }

}
