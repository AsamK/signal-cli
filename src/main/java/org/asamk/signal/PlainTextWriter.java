package org.asamk.signal;

public interface PlainTextWriter {

    void println(String format, Object... args);

    PlainTextWriter indentedWriter();

    default void println() {
        println("");
    }

    default void indent(final WriterConsumer subWriter) {
        subWriter.consume(indentedWriter());
    }

    interface WriterConsumer {

        void consume(PlainTextWriter writer);
    }
}
