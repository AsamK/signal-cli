package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JsonRpcLocalCommand extends JsonRpcCommand<Map<String, Object>> {

    void handleCommand(Namespace ns, Manager m) throws CommandException;

    default TypeReference<Map<String, Object>> getRequestType() {
        return new TypeReference<>() {
        };
    }

    default void handleCommand(Map<String, Object> request, Manager m) throws CommandException {
        Namespace commandNamespace = new JsonRpcNamespace(request == null ? Map.of() : request);
        handleCommand(commandNamespace, m);
    }

    default Set<OutputType> getSupportedOutputTypes() {
        return Set.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    /**
     * Namepace implementation, that defaults booleans to false and converts camel case keys to dashed strings
     */
    final class JsonRpcNamespace extends Namespace {

        public JsonRpcNamespace(final Map<String, Object> attrs) {
            super(attrs);
        }

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
            final List<E> value = super.getList(dest);
            if (value != null) {
                return value;
            }

            return super.getList(dest + "s");
        }

        @Override
        public Boolean getBoolean(String dest) {
            Boolean maybeGotten = this.get(dest);
            if (maybeGotten == null) {
                maybeGotten = false;
            }
            return maybeGotten;
        }
    }
}
