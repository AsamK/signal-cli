package org.asamk.signal.commands;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.SendMessageResults;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SendReceiptCommandTest {

    private static final String ACI = "84edbab3-8d1f-48f1-8bae-debb7bde3c6e";

    @Test
    void jsonRpcAcceptsRecipientAsSingleValue() throws Exception {
        assertEquals(new RecipientIdentifier.Uuid(UUID.fromString(ACI)), sendReceipt(ACI));
    }

    @Test
    void jsonRpcAcceptsRecipientAsListWithOneEntry() throws Exception {
        // Like the recipient of the other send commands
        assertEquals(new RecipientIdentifier.Uuid(UUID.fromString(ACI)), sendReceipt(List.of(ACI)));
        assertEquals(new RecipientIdentifier.Number("+4915187654321"), sendReceipt(List.of("+4915187654321")));
    }

    private RecipientIdentifier.Single sendReceipt(final Object recipient) throws Exception {
        final var captured = new AtomicReference<RecipientIdentifier.Single>();
        final var delegate = ManagerMock.create("+4915112345678");
        final var manager = (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendReadReceipt")) {
                        captured.set((RecipientIdentifier.Single) args[0]);
                        return new SendMessageResults(1, Map.of());
                    }
                    return method.invoke(delegate, args);
                });
        new SendReceiptCommand().handleCommand(Map.of("recipient", recipient, "targetTimestamp", 1700000000000L),
                manager,
                (JsonWriter) ignored -> {
                });
        return captured.get();
    }
}
