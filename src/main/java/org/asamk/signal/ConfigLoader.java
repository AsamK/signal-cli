package org.asamk.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.asamk.signal.commands.exceptions.UserErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads and merges configuration files. Merge order (later files override earlier):
 * - /etc/signal-cli/config.json
 * - file pointed to by SIGNAL_CLI_CONFIG (if set)
 * - $XDG_CONFIG_HOME/signal-cli/config.json or $HOME/.config/signal-cli/config.json
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static GlobalConfig load() throws UserErrorException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode merged = mapper.createObjectNode();

        // System config
        addIfExists(merged, mapper, Paths.get("/etc/signal-cli/config.json"));

        // User config via env (if set) else XDG or ~/.config
        final String env = System.getenv("SIGNAL_CLI_CONFIG");
        if (env != null && !env.isEmpty()) {
            addIfExists(merged, mapper, Paths.get(env));
        } else {
            final String xdg = System.getenv("XDG_CONFIG_HOME");
            if (xdg != null && !xdg.isEmpty()) {
                addIfExists(merged, mapper, Paths.get(xdg, "signal-cli", "config.json"));
            } else {
                addIfExists(merged,
                        mapper,
                        Paths.get(System.getProperty("user.home"), ".config", "signal-cli", "config.json"));
            }
        }

        try {
            if (merged.isEmpty()) {
                return GlobalConfig.DEFAULT;
            }
            return mapper.treeToValue(merged, GlobalConfig.class);
        } catch (Exception e) {
            throw new UserErrorException("Failed to parse configuration file(s): " + e.getMessage(), e);
        }
    }

    private static void addIfExists(ObjectNode merged, ObjectMapper mapper, Path p) throws UserErrorException {
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                final JsonNode node = mapper.readTree(p.toFile());
                merge(merged, node);
            }
        } catch (IOException e) {
            throw new UserErrorException("Failed to load config from " + p + ": " + e.getMessage(), e);
        }
    }

    private static void merge(ObjectNode target, JsonNode source) {
        source.properties().forEach(entry -> {
            final String name = entry.getKey();
            final JsonNode value = entry.getValue();
            final JsonNode existing = target.get(name);
            if (existing != null && existing.isObject() && value.isObject()) {
                merge((ObjectNode) existing, value);
            } else {
                target.set(name, value);
            }
        });
    }
}
