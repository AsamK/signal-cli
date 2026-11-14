package org.asamk.signal.commands;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Signal clients drop a poll whose question is blank or longer than 200 characters, so such a
 * poll must be rejected before it is sent instead of being reported as sent.
 */
class SendPollCreateCommandTest {

    private final AtomicInteger sentPolls = new AtomicInteger();

    @ParameterizedTest
    @ValueSource(ints = {0, 201})
    void questionWithInvalidLengthIsRejected(final int length) {
        assertThrows(UserErrorException.class, () -> sendPoll("q".repeat(length)));
        assertEquals(0, sentPolls.get());
    }

    @Test
    void blankQuestionIsRejected() {
        assertThrows(UserErrorException.class, () -> sendPoll("   "));
        assertEquals(0, sentPolls.get());
    }

    @Test
    void questionWithMaximumLengthIsSent() throws Exception {
        sendPoll("q".repeat(200));
        assertEquals(1, sentPolls.get());
    }

    private void sendPoll(final String question) throws Exception {
        final var delegate = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendPollCreateMessage")) {
                        sentPolls.incrementAndGet();
                        return new SendMessageResults(1L, Map.of());
                    }
                    return method.invoke(delegate, args);
                });
        new SendPollCreateCommand().handleCommand(new JsonRpcNamespace(Map.of("noteToSelf",
                true,
                "question",
                question,
                "option",
                List.of("Approve", "Hold"))), manager, (JsonWriter) ignored -> {});
    }
}
