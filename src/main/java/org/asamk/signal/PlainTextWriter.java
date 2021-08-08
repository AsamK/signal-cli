package org.asamk.signal;

import java.util.function.Consumer;

public interface PlainTextWriter extends OutputWriter {

    void println(String format, Object... args);

    PlainTextWriter indentedWriter();

    default void println() {
        println("");
    }

    default void indent(final Consumer<PlainTextWriter> subWriter) {
        subWriter.accept(indentedWriter());
    }
}
