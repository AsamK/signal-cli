package org.asamk.signal.output;

import java.util.function.Consumer;

public non-sealed interface PlainTextWriter extends OutputWriter {

    void println(String format, Object... args);

    PlainTextWriter indentedWriter();

    default void println() {
        println("");
    }

    default void indent(final Consumer<PlainTextWriter> subWriter) {
        subWriter.accept(indentedWriter());
    }
}
