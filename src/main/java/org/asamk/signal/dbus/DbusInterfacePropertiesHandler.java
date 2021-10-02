package org.asamk.signal.dbus;

import org.asamk.Signal;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DbusInterfacePropertiesHandler {

    private final String interfaceName;
    private final List<DbusProperty<?>> properties;

    public DbusInterfacePropertiesHandler(
            final String interfaceName, final List<DbusProperty<?>> properties
    ) {
        this.interfaceName = interfaceName;
        this.properties = properties;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    @SuppressWarnings("unchecked")
    private <T> DbusProperty<T> findProperty(String propertyName) {
        final var property = properties.stream().filter(p -> p.getName().equals(propertyName)).findFirst();
        if (property.isEmpty()) {
            throw new Signal.Error.Failure("Property not found");
        }
        return (DbusProperty<T>) property.get();
    }

    <T> Consumer<T> getSetter(String propertyName) {
        return this.<T>findProperty(propertyName).getSetter();
    }

    <T> Supplier<T> getGetter(String propertyName) {
        return this.<T>findProperty(propertyName).getGetter();
    }

    Collection<DbusProperty<?>> getProperties() {
        return properties;
    }
}
