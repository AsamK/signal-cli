package org.asamk.signal.http;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * This class send Server-sent events payload to an OutputStream.
 * See <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">spec</a>
 */
public class ServerSentEventSender {

    private final BufferedWriter writer;

    public ServerSentEventSender(final OutputStream outputStream) {
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    /**
     * @param id    Event id
     * @param event Event type
     * @param data  Event data, each entry must not contain newline chars.
     */
    public synchronized void sendEvent(String id, String event, List<String> data) throws IOException {
        if (id != null) {
            writer.write("id:");
            writer.write(id);
            writer.write("\n");
        }
        if (event != null) {
            writer.write("event:");
            writer.write(event);
            writer.write("\n");
        }
        if (data.size() == 0) {
            writer.write("data\n");
        } else {
            for (final var d : data) {
                writer.write("data:");
                writer.write(d);
                writer.write("\n");
            }
        }
        writer.write("\n");
        writer.flush();
    }

    public synchronized void sendKeepAlive() throws IOException {
        writer.write(":\n");
        writer.flush();
    }
}
