package org.asamk.signal;

import java.io.IOException;

public interface PlainTextWriter {

    void println(String format, Object... args) throws IOException;

    PlainTextWriter indentedWriter();

    default void println() throws IOException {
        println("");
    }

    default void indent(final WriterConsumer subWriter) throws IOException {
        subWriter.consume(indentedWriter());
    }

    interface WriterConsumer {

        void consume(PlainTextWriter writer) throws IOException;
    }
}
