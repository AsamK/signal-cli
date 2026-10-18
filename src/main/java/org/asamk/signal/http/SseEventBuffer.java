package org.asamk.signal.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.json.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the most recent SSE events and sends new events to the connected clients.
 * A client that reconnects with a Last-Event-ID header first gets the buffered events it missed.
 * <p>
 * Event ids have the form "{start time}-{sequence}", so an id from an earlier daemon run never matches.
 */
final class SseEventBuffer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SseEventBuffer.class);

    static final int MAX_EVENTS = 1000;

    private final ObjectMapper objectMapper;
    private final String idPrefix = System.currentTimeMillis() + "-";
    private final Map<Manager, Manager.ReceiveMessageHandler> recorders = new HashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final List<Client> clients = new ArrayList<>();
    private long nextSequence = 1;

    SseEventBuffer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Start recording the messages of the given account.
     * The handler is registered as a weak listener, so it never starts receiving by itself.
     */
    void record(final Manager m) {
        synchronized (recorders) {
            if (recorders.containsKey(m)) {
                return;
            }
            final var handler = new JsonReceiveMessageHandler(m, s -> add(m, s));
            m.addReceiveHandler(handler, true);
            recorders.put(m, handler);
        }
    }

    /**
     * Register a client for the given accounts.
     * If lastEventId is set, the buffered events after it are sent first.
     * An unknown id (too old, or from an earlier run) sends all buffered events.
     */
    synchronized Client connect(
            final Collection<Manager> managers,
            final ServerSentEventSender sender,
            final String lastEventId,
            final Runnable onClosed
    ) {
        final var client = new Client(managers, sender, onClosed);
        if (lastEventId != null) {
            final var lastSequence = getSequence(lastEventId);
            for (final var event : events) {
                if (event.sequence() > lastSequence && managers.contains(event.manager())) {
                    client.send(event);
                }
            }
        }
        clients.add(client);
        return client;
    }

    synchronized void disconnect(final Client client) {
        clients.remove(client);
    }

    @Override
    public void close() {
        synchronized (recorders) {
            recorders.forEach(Manager::removeReceiveHandler);
            recorders.clear();
        }
    }

    private void add(final Manager m, final Object message) {
        final String data;
        try {
            data = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            logger.warn("Failed to serialize received message, ignoring", e);
            return;
        }

        final Event event;
        final List<Client> receivers;
        synchronized (this) {
            event = new Event(nextSequence++, m, data);
            events.addLast(event);
            if (events.size() > MAX_EVENTS) {
                events.removeFirst();
            }
            receivers = clients.stream().filter(c -> c.managers.contains(m)).toList();
        }
        // Sent outside the lock, so a slow client doesn't block the other accounts
        for (final var client : receivers) {
            client.send(event);
        }
    }

    private long getSequence(final String eventId) {
        if (eventId.startsWith(idPrefix)) {
            try {
                return Long.parseLong(eventId.substring(idPrefix.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private record Event(long sequence, Manager manager, String data) {}

    final class Client {

        private final Collection<Manager> managers;
        private final ServerSentEventSender sender;
        private final Runnable onClosed;

        private Client(
                final Collection<Manager> managers,
                final ServerSentEventSender sender,
                final Runnable onClosed
        ) {
            this.managers = managers;
            this.sender = sender;
            this.onClosed = onClosed;
        }

        private void send(final Event event) {
            try {
                sender.sendEvent(idPrefix + event.sequence(), "receive", List.of(event.data()));
            } catch (IOException e) {
                onClosed.run();
            }
        }
    }
}
