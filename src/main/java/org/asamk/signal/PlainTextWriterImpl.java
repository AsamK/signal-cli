package org.asamk.signal;

import org.slf4j.helpers.MessageFormatter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public final class PlainTextWriterImpl implements PlainTextWriter {

    private final Writer writer;

    private PlainTextWriter indentedWriter;

    public PlainTextWriterImpl(final OutputStream outputStream) {
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }

    @Override
    public void println(String format, Object... args) {
        final var message = MessageFormatter.arrayFormat(format, args).getMessage();

        try {
            writer.write(message);
            writer.write(System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public PlainTextWriter indentedWriter() {
        if (indentedWriter == null) {
            indentedWriter = new IndentedPlainTextWriter(this, writer);
        }
        return indentedWriter;
    }

    private static final class IndentedPlainTextWriter implements PlainTextWriter {

        private final static int INDENTATION = 2;

        private final String spaces = " ".repeat(INDENTATION);
        private final PlainTextWriter plainTextWriter;
        private final Writer writer;

        private PlainTextWriter indentedWriter;

        private IndentedPlainTextWriter(final PlainTextWriter plainTextWriter, final Writer writer) {
            this.plainTextWriter = plainTextWriter;
            this.writer = writer;
        }

        @Override
        public void println(final String format, final Object... args) {
            try {
                writer.write(spaces);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            plainTextWriter.println(format, args);
        }

        @Override
        public PlainTextWriter indentedWriter() {
            if (indentedWriter == null) {
                indentedWriter = new IndentedPlainTextWriter(this, writer);
            }
            return indentedWriter;
        }
    }
}
