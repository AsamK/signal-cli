package org.asamk.signal.commands;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetUserStatusCommand implements LocalCommand {

    // TODO delete later when "json" variable is removed
    private final static Logger logger = LoggerFactory.getLogger(GetUserStatusCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("number").help("Phone number").nargs("+");
        subparser.help("Check if the specified phone number/s have been registered");
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the global \"--output=json\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        // Setup the json object mapper
        ObjectMapper jsonProcessor = new ObjectMapper();
        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        boolean inJson = ns.getString("output").equals("json") || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        // Get a map of registration statuses
        Map<String, Boolean> registered;
        try {
            registered = m.areUsersRegistered(new HashSet<>(ns.getList("number")));
        } catch (IOException e) {
            System.err.println("Unable to check if users are registered");
            return 1;
        }

        // Output
        if (inJson) {
            List<JsonIsRegistered> objects = registered.entrySet()
                    .stream()
                    .map(entry -> new JsonIsRegistered(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            try {
                jsonProcessor.writeValue(System.out, objects);
                System.out.println();
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

    private static final class JsonIsRegistered {

        public String name;

        public boolean isRegistered;

        public JsonIsRegistered(String name, boolean isRegistered) {
            this.name = name;
            this.isRegistered = isRegistered;
        }
    }
}
