package org.asamk.signal.commands;

import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SendCommandErrorTest {

    @ParameterizedTest
    @MethodSource("failures")
    void uploadAndTransmissionFailuresKeepTheirClassification(final Exception cause) {
        final var error = send(cause);

        assertEquals("Failed to send message: " + cause.getMessage() + " (" + cause.getClass().getSimpleName() + ")",
                error.getMessage());
        assertSame(cause, error.getCause());
    }

    private static Stream<Exception> failures() {
        return Stream.of(new AttachmentInvalidException("inline attachment #2: Connection reset"),
                new SocketException("Connection reset"),
                new IOException("Server error: 500"));
    }

    private static UnexpectedErrorException send(final Exception failure) {
        final var delegate = ManagerMock.create("+15551234567");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) {
                        throw failure;
                    }
                    return method.invoke(delegate, args);
                });
        // Upload and transmission failures must map to the same exception type, so the exit code and the
        // JSON-RPC error code stay the same.
        return assertThrows(UnexpectedErrorException.class,
                () -> new SendCommand().handleCommand(new JsonRpcNamespace(Map.of("noteToSelf",
                        true,
                        "message",
                        "hi",
                        "attachments",
                        List.of("photo.jpg"))), manager, (JsonWriter) ignored -> {}));
    }
}
