package org.asamk.signal.dbus;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DbusProperty<T> {

    private final String name;
    private final Supplier<T> getter;
    private final Consumer<T> setter;

    public DbusProperty(final String name, final Supplier<T> getter, final Consumer<T> setter) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
    }

    public DbusProperty(final String name, final Supplier<T> getter) {
        this.name = name;
        this.getter = getter;
        this.setter = null;
    }

    public DbusProperty(final String name, final Consumer<T> setter) {
        this.name = name;
        this.getter = null;
        this.setter = setter;
    }

    public String getName() {
        return name;
    }

    public Consumer<T> getSetter() {
        return setter;
    }

    public Supplier<T> getGetter() {
        return getter;
    }
}
