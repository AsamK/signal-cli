package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.util.Util;

import java.util.List;
import java.util.Map;

/**
 * Namespace implementation, that has plural handling for list arguments and converts camel case keys to dashed strings.
 * A list with a single entry is also accepted for a single value argument.
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
    public String getString(final String dest) {
        final Object value = get(dest);
        if (value instanceof List<?> list && list.size() == 1) {
            final var entry = list.getFirst();
            return entry == null ? null : entry.toString();
        }
        return value == null ? null : value.toString();
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
