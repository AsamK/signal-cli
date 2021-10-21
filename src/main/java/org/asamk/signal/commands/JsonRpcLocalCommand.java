package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputType;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;

import java.util.List;
import java.util.Map;

public interface JsonRpcLocalCommand extends JsonRpcCommand<Map<String, Object>>, LocalCommand {

    default TypeReference<Map<String, Object>> getRequestType() {
        return new TypeReference<>() {
        };
    }

    default void handleCommand(
            Map<String, Object> request, Manager m, OutputWriter outputWriter
    ) throws CommandException {
        Namespace commandNamespace = new JsonRpcNamespace(request == null ? Map.of() : request);
        handleCommand(commandNamespace, m, outputWriter);
    }

    default List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    /**
     * Namespace implementation, that has plural handling for list arguments and converts camel case keys to dashed strings
     */
    final class JsonRpcNamespace extends Namespace {

        public JsonRpcNamespace(final Map<String, Object> attrs) {
            super(attrs);
        }

        @Override
        public <T> T get(String dest) {
            final T value = super.get(dest);
            if (value != null) {
                return value;
            }

            final var camelCaseString = Util.dashSeparatedToCamelCaseString(dest);
            return super.get(camelCaseString);
        }

        @Override
        public <E> List<E> getList(final String dest) {
            try {
                final List<E> value = super.getList(dest);
                if (value != null) {
                    return value;
                }
            } catch (ClassCastException e) {
                return List.of(this.<E>get(dest));
            }

            return super.getList(dest + "s");
        }
    }
}
