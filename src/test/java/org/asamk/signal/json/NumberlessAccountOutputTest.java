package org.asamk.signal.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.commands.ListAccountsCommand;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.internal.MultiAccountManagerImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class NumberlessAccountOutputTest {

    private static final String ACI = "11111111-1111-4111-8111-111111111111";

    @Test
    void receiveEventsIdentifyNumberlessAccountByAci() {
        final var output = new AtomicReference<Object>();
        new JsonReceiveMessageHandler(manager(null), output::set).handleMessage(null, null);
        assertEquals(ACI, ((Map<?, ?>) output.get()).get("account"));
    }

    @Test
    void receiveEventsKeepNumberedAccountIdentifiers() {
        final var output = new AtomicReference<Object>();
        new JsonReceiveMessageHandler(manager("+12025550123"), output::set).handleMessage(null, null);
        assertEquals("+12025550123", ((Map<?, ?>) output.get()).get("account"));
    }

    @Test
    void listAccountsIncludesAciWithoutPuttingItInTheNumberField() throws Exception {
        final var numberless = manager(null);
        final var numbered = manager("+12025550123");
        final var multi = new MultiAccountManagerImpl(List.of(numberless, numbered), null);
        final var output = new AtomicReference<Object>();
        new ListAccountsCommand().handleCommand(Map.of(), multi, output::set);
        final var accounts = new ObjectMapper().valueToTree(output.get());
        assertEquals(2, accounts.size());
        for (final var account : accounts) {
            assertEquals(ACI, account.path("aci").asText());
            assertFalse(ACI.equals(account.path("number").asText()));
        }
        assertSame(numbered, multi.getManager("+12025550123"));
    }

    private static Manager manager(final String number) {
        return (Manager) Proxy.newProxyInstance(Manager.class.getClassLoader(), new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    return switch (method.getName()) {
                        case "getSelfNumber" -> number;
                        case "getSelfACI" -> ACI;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "addClosedListener" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }
}
