package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class DbusProperties implements Properties {

    private final List<DbusInterfacePropertiesHandler> handlers = new ArrayList<>();

    protected void addPropertiesHandler(DbusInterfacePropertiesHandler handler) {
        this.handlers.add(handler);
    }

    DbusInterfacePropertiesHandler getHandler(String interfaceName) {
        final var handler = getHandlerOptional(interfaceName);
        if (handler.isEmpty()) {
            throw new Signal.Error.Failure("Property not found");
        }
        return handler.get();
    }

    private Optional<DbusInterfacePropertiesHandler> getHandlerOptional(final String interfaceName) {
        return handlers.stream().filter(h -> h.getInterfaceName().equals(interfaceName)).findFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A Get(final String interface_name, final String property_name) {
        final var handler = getHandler(interface_name);
        final var getter = handler.getGetter(property_name);
        if (getter == null) {
            throw new Signal.Error.Failure("Property not found");
        }
        return (A) getter.get();
    }

    @Override
    public <A> void Set(final String interface_name, final String property_name, final A value) {
        final var handler = getHandler(interface_name);
        final var setter = handler.getSetter(property_name);
        if (setter == null) {
            throw new Signal.Error.Failure("Property not found");
        }
        setter.accept(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Variant<?>> GetAll(final String interface_name) {
        final var handler = getHandlerOptional(interface_name);
        if (handler.isEmpty()) {
            return Map.of();
        }

        return handler.get()
                .getProperties()
                .stream()
                .filter(p -> p.getGetter() != null)
                .collect(Collectors.toMap(DbusProperty::getName, p -> {
                    final Object o = p.getGetter().get();
                    return o instanceof Variant ? (Variant<Object>) o : new Variant<>(o);
                }));
    }
}
